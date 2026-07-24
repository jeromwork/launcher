//! Real MLS (RFC 9420) engine behind UniFFI (TASK-124, T004–T008).
//!
//! Every verb here is **stateless across the FFI boundary**: it takes a serialized storage
//! snapshot ([`crate::storage`]), rebuilds an openmls provider from it, loads the `MlsGroup`
//! (openmls 0.8 `MlsGroup` is not serializable — it is reconstructed via `MlsGroup::load`),
//! performs the operation, and returns the updated snapshot plus the operation result.
//! Kotlin (`family.crypto.mls`) owns the snapshot bytes. See
//! `specs/task-124-openmls-integration-in-memory/contracts/mls-ffi-surface.md`.
//!
//! **Identity / signing key.** The signing key is generated *here*, per group, and stored in the
//! snapshot (ephemeral, in-memory only). It is NOT the device identity key.
//! TODO(task-112): derive the MLS signature key from `KeyVault.exportDerivedKey(MLS_SIGNATURE, …)`
//! and pass it in, instead of generating it in-Rust (docs/architecture/crypto-key-hierarchy.md).
//!
//! **A member's identity == its Ed25519 signature public key** (matches the domain's `IdentityKey`,
//! `family.crypto.ports.Values`). The BasicCredential identity carries the same bytes, so
//! `remove_members` resolves `identity → LeafNodeIndex` off the group roster without parsing
//! credentials.
//!
//! All verbs return `Result<_, MlsError>` (throwing signatures) — no `unwrap` on caller-supplied
//! bytes, so malformed input surfaces as a Kotlin exception, never a process abort.

use openmls::prelude::{tls_codec::Deserialize as _, *};
use openmls_basic_credential::SignatureKeyPair;
use openmls_rust_crypto::OpenMlsRustCrypto;
use openmls_traits::OpenMlsProvider;

use crate::storage::{provider_from_snapshot, snapshot_from_provider};

/// The one ciphersuite we support (arch-pack: `docs/architecture/crypto-mls.md`).
const CIPHERSUITE: Ciphersuite = Ciphersuite::MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519;

// ---------------------------------------------------------------------------
// Error surface
// ---------------------------------------------------------------------------

/// Errors crossing the FFI boundary. Mapped by UniFFI to a Kotlin exception per variant.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum MlsError {
    /// Snapshot could not be read or written.
    #[error("storage error: {msg}")]
    Storage { msg: String },

    /// `create_group` called for a group id that already exists in this snapshot.
    #[error("group already exists")]
    GroupExists,

    /// The group id is absent from this snapshot.
    #[error("unknown group")]
    UnknownGroup,

    /// Caller-supplied bytes are not a well-formed MLS message / KeyPackage.
    #[error("malformed MLS message: {msg}")]
    MalformedMessage { msg: String },

    /// `merge_pending_commit` with nothing staged (the domain's two-phase contract).
    #[error("no pending commit to merge")]
    NoPendingCommit,

    /// `remove_members` for an identity that is not in the group roster.
    #[error("unknown member")]
    UnknownMember,

    /// The signing key for this group is missing from the snapshot.
    #[error("missing signer for group")]
    MissingSigner,

    /// Any other openmls-side failure (validation, state machine, crypto).
    #[error("mls operation failed: {msg}")]
    Operation { msg: String },
}

fn op<E: std::fmt::Display>(e: E) -> MlsError {
    MlsError::Operation { msg: e.to_string() }
}

fn malformed<E: std::fmt::Display>(e: E) -> MlsError {
    MlsError::MalformedMessage { msg: e.to_string() }
}

// ---------------------------------------------------------------------------
// Result records
// ---------------------------------------------------------------------------

/// A verb that only advances state, plus the caller's own identity where relevant.
#[derive(uniffi::Record)]
pub struct StateResult {
    pub state: Vec<u8>,
    /// Ed25519 signature public key of the local member (the domain's `IdentityKey`).
    pub identity: Vec<u8>,
}

/// A staged group change: the commit to fan out and, for adds, the Welcome.
#[derive(uniffi::Record)]
pub struct CommitResult {
    pub state: Vec<u8>,
    pub commit: Vec<u8>,
    pub welcome: Option<Vec<u8>>,
}

/// `commit_to_pending_proposals` — `commit` is `None` when there was nothing to commit.
#[derive(uniffi::Record)]
pub struct OptionalCommitResult {
    pub state: Vec<u8>,
    pub commit: Option<Vec<u8>>,
    pub welcome: Option<Vec<u8>>,
}

/// Only the updated snapshot.
#[derive(uniffi::Record)]
pub struct SnapshotResult {
    pub state: Vec<u8>,
}

#[derive(uniffi::Record)]
pub struct EncryptResult {
    pub state: Vec<u8>,
    pub ciphertext: Vec<u8>,
}

#[derive(uniffi::Record)]
pub struct DecryptResult {
    pub state: Vec<u8>,
    pub plaintext: Vec<u8>,
}

/// Which `ProcessedMessage` variant openmls produced.
#[derive(uniffi::Enum)]
pub enum ProcessedKind {
    Application,
    StagedCommit,
    Proposal,
}

/// `process_message` outcome. For [`ProcessedKind::Application`] the payload is the plaintext;
/// for the other kinds it is the original message bytes (the openmls `StagedCommit` /
/// `QueuedProposal` objects are not serializable, so the caller re-supplies the bytes to
/// [`mls_merge_staged_commit`]).
#[derive(uniffi::Record)]
pub struct ProcessResult {
    pub state: Vec<u8>,
    pub kind: ProcessedKind,
    pub payload: Vec<u8>,
}

#[derive(uniffi::Record)]
pub struct KeyPackageResult {
    pub state: Vec<u8>,
    pub key_package: Vec<u8>,
    /// Ed25519 signature public key bound to this KeyPackage.
    pub identity: Vec<u8>,
}

#[derive(uniffi::Record)]
pub struct JoinResult {
    pub state: Vec<u8>,
    pub group_id: Vec<u8>,
    pub identity: Vec<u8>,
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

fn create_config() -> MlsGroupCreateConfig {
    MlsGroupCreateConfig::builder()
        .ciphersuite(CIPHERSUITE)
        // Ship the ratchet tree inside the Welcome so a joiner needs nothing else out-of-band.
        .use_ratchet_tree_extension(true)
        .build()
}

fn join_config() -> MlsGroupJoinConfig {
    MlsGroupJoinConfig::builder()
        .use_ratchet_tree_extension(true)
        .build()
}

/// Generate a fresh ephemeral identity and persist it in the snapshot.
fn new_identity(
    provider: &OpenMlsRustCrypto,
) -> Result<(SignatureKeyPair, CredentialWithKey), MlsError> {
    let signer = SignatureKeyPair::new(CIPHERSUITE.signature_algorithm()).map_err(op)?;
    signer.store(provider.storage()).map_err(|e| MlsError::Storage {
        msg: e.to_string(),
    })?;
    // Identity bytes == signature public key: the domain's `IdentityKey` is exactly this.
    let credential = BasicCredential::new(signer.public().to_vec());
    let credential_with_key = CredentialWithKey {
        credential: credential.into(),
        signature_key: signer.public().into(),
    };
    Ok((signer, credential_with_key))
}

fn load_group(provider: &OpenMlsRustCrypto, group_id: &[u8]) -> Result<MlsGroup, MlsError> {
    let gid = GroupId::from_slice(group_id);
    MlsGroup::load(provider.storage(), &gid)
        .map_err(|e| MlsError::Storage { msg: e.to_string() })?
        .ok_or(MlsError::UnknownGroup)
}

/// Read back the signing key of the local member from the snapshot.
fn signer_for(provider: &OpenMlsRustCrypto, group: &MlsGroup) -> Result<SignatureKeyPair, MlsError> {
    let leaf = group.own_leaf_node().ok_or(MlsError::MissingSigner)?;
    SignatureKeyPair::read(
        provider.storage(),
        leaf.signature_key().as_slice(),
        CIPHERSUITE.signature_algorithm(),
    )
    .ok_or(MlsError::MissingSigner)
}

fn own_identity(group: &MlsGroup) -> Vec<u8> {
    group
        .own_leaf_node()
        .map(|leaf| leaf.signature_key().as_slice().to_vec())
        .unwrap_or_default()
}

fn message_bytes(message: &MlsMessageOut) -> Result<Vec<u8>, MlsError> {
    message.to_bytes().map_err(op)
}

fn parse_message(bytes: &[u8]) -> Result<MlsMessageIn, MlsError> {
    MlsMessageIn::tls_deserialize_exact(bytes).map_err(malformed)
}

fn parse_key_package(
    provider: &OpenMlsRustCrypto,
    bytes: &[u8],
) -> Result<KeyPackage, MlsError> {
    match parse_message(bytes)?.extract() {
        MlsMessageBodyIn::KeyPackage(kp) => kp
            .validate(provider.crypto(), ProtocolVersion::Mls10)
            .map_err(malformed),
        _ => Err(MlsError::MalformedMessage {
            msg: "not a KeyPackage".to_string(),
        }),
    }
}

// ---------------------------------------------------------------------------
// Verbs
// ---------------------------------------------------------------------------

/// Create a brand-new single-member group. Errors if the group id already exists (FR-003).
#[uniffi::export]
pub fn mls_create_group(state: Vec<u8>, group_id: Vec<u8>) -> Result<StateResult, MlsError> {
    let provider = provider_from_snapshot(&state)?;
    let gid = GroupId::from_slice(&group_id);

    if MlsGroup::load(provider.storage(), &gid)
        .map_err(|e| MlsError::Storage { msg: e.to_string() })?
        .is_some()
    {
        return Err(MlsError::GroupExists);
    }

    let (signer, credential_with_key) = new_identity(&provider)?;
    let group = MlsGroup::new_with_group_id(
        &provider,
        &signer,
        &create_config(),
        gid,
        credential_with_key,
    )
    .map_err(op)?;

    Ok(StateResult {
        state: snapshot_from_provider(&provider)?,
        identity: own_identity(&group),
    })
}

/// Stage adding `key_packages`; returns the commit and the Welcome to fan out (FR-004).
#[uniffi::export]
pub fn mls_add_members(
    state: Vec<u8>,
    group_id: Vec<u8>,
    key_packages: Vec<Vec<u8>>,
) -> Result<CommitResult, MlsError> {
    let provider = provider_from_snapshot(&state)?;
    let mut group = load_group(&provider, &group_id)?;
    let signer = signer_for(&provider, &group)?;

    let parsed: Vec<KeyPackage> = key_packages
        .iter()
        .map(|bytes| parse_key_package(&provider, bytes))
        .collect::<Result<_, _>>()?;

    let (commit, welcome, _group_info) = group
        .add_members(&provider, &signer, &parsed)
        .map_err(op)?;

    Ok(CommitResult {
        state: snapshot_from_provider(&provider)?,
        commit: message_bytes(&commit)?,
        welcome: Some(message_bytes(&welcome)?),
    })
}

/// Stage removing members identified by their Ed25519 signature public keys (FR-007).
#[uniffi::export]
pub fn mls_remove_members(
    state: Vec<u8>,
    group_id: Vec<u8>,
    member_identities: Vec<Vec<u8>>,
) -> Result<CommitResult, MlsError> {
    let provider = provider_from_snapshot(&state)?;
    let mut group = load_group(&provider, &group_id)?;
    let signer = signer_for(&provider, &group)?;

    // Resolve identity → leaf index off the roster; an unknown identity is a deterministic error.
    let roster: Vec<Member> = group.members().collect();
    let mut indices = Vec::with_capacity(member_identities.len());
    for identity in &member_identities {
        let member = roster
            .iter()
            .find(|m| &m.signature_key == identity)
            .ok_or(MlsError::UnknownMember)?;
        indices.push(member.index);
    }

    let (commit, welcome, _group_info) = group
        .remove_members(&provider, &signer, &indices)
        .map_err(op)?;

    Ok(CommitResult {
        state: snapshot_from_provider(&provider)?,
        commit: message_bytes(&commit)?,
        welcome: welcome.as_ref().map(message_bytes).transpose()?,
    })
}

/// Stage a self-update (rotate own leaf key) for post-compromise security (FR-004).
#[uniffi::export]
pub fn mls_self_update(state: Vec<u8>, group_id: Vec<u8>) -> Result<CommitResult, MlsError> {
    let provider = provider_from_snapshot(&state)?;
    let mut group = load_group(&provider, &group_id)?;
    let signer = signer_for(&provider, &group)?;

    let bundle = group
        .self_update(&provider, &signer, LeafNodeParameters::default())
        .map_err(op)?;
    let welcome = bundle
        .to_welcome_msg()
        .as_ref()
        .map(message_bytes)
        .transpose()?;
    let commit = message_bytes(bundle.commit())?;

    Ok(CommitResult {
        state: snapshot_from_provider(&provider)?,
        commit,
        welcome,
    })
}

/// Stage a commit covering all pending proposals; `commit == None` when there were none (FR-004).
#[uniffi::export]
pub fn mls_commit_to_pending_proposals(
    state: Vec<u8>,
    group_id: Vec<u8>,
) -> Result<OptionalCommitResult, MlsError> {
    let provider = provider_from_snapshot(&state)?;
    let mut group = load_group(&provider, &group_id)?;
    let signer = signer_for(&provider, &group)?;

    if group.pending_proposals().next().is_none() {
        return Ok(OptionalCommitResult {
            state,
            commit: None,
            welcome: None,
        });
    }

    let (commit, welcome, _group_info) = group
        .commit_to_pending_proposals(&provider, &signer)
        .map_err(op)?;

    Ok(OptionalCommitResult {
        state: snapshot_from_provider(&provider)?,
        commit: Some(message_bytes(&commit)?),
        welcome: welcome.as_ref().map(message_bytes).transpose()?,
    })
}

/// Merge the locally staged commit, advancing the epoch.
///
/// Deviation from raw openmls: `merge_pending_commit` is a no-op there when nothing is staged;
/// the domain port contract requires a deterministic error, so we check first (FR-006).
#[uniffi::export]
pub fn mls_merge_pending_commit(
    state: Vec<u8>,
    group_id: Vec<u8>,
) -> Result<SnapshotResult, MlsError> {
    let provider = provider_from_snapshot(&state)?;
    let mut group = load_group(&provider, &group_id)?;

    if group.pending_commit().is_none() {
        return Err(MlsError::NoPendingCommit);
    }
    group.merge_pending_commit(&provider).map_err(op)?;

    Ok(SnapshotResult {
        state: snapshot_from_provider(&provider)?,
    })
}

/// Merge an inbound commit (previously seen via [`mls_process_message`]) into the group.
///
/// The openmls `StagedCommit` is not serializable, so the caller re-supplies the commit bytes and
/// we re-process them here before merging.
#[uniffi::export]
pub fn mls_merge_staged_commit(
    state: Vec<u8>,
    group_id: Vec<u8>,
    commit: Vec<u8>,
) -> Result<SnapshotResult, MlsError> {
    let provider = provider_from_snapshot(&state)?;
    let mut group = load_group(&provider, &group_id)?;

    let message = parse_message(&commit)?;
    let protocol_message: ProtocolMessage = message
        .try_into_protocol_message()
        .map_err(|_| MlsError::MalformedMessage {
            msg: "not a protocol message".to_string(),
        })?;
    let processed = group
        .process_message(&provider, protocol_message)
        .map_err(op)?;

    match processed.into_content() {
        ProcessedMessageContent::StagedCommitMessage(staged) => {
            group.merge_staged_commit(&provider, *staged).map_err(op)?;
            Ok(SnapshotResult {
                state: snapshot_from_provider(&provider)?,
            })
        }
        _ => Err(MlsError::MalformedMessage {
            msg: "message is not a commit".to_string(),
        }),
    }
}

/// Process an inbound MLS message and report which variant it was (FR-004).
#[uniffi::export]
pub fn mls_process_message(
    state: Vec<u8>,
    group_id: Vec<u8>,
    message: Vec<u8>,
) -> Result<ProcessResult, MlsError> {
    let provider = provider_from_snapshot(&state)?;
    let mut group = load_group(&provider, &group_id)?;

    let parsed = parse_message(&message)?;
    let protocol_message: ProtocolMessage =
        parsed
            .try_into_protocol_message()
            .map_err(|_| MlsError::MalformedMessage {
                msg: "not a protocol message".to_string(),
            })?;
    let processed = group
        .process_message(&provider, protocol_message)
        .map_err(op)?;

    let (kind, payload) = match processed.into_content() {
        ProcessedMessageContent::ApplicationMessage(app) => {
            (ProcessedKind::Application, app.into_bytes())
        }
        // The staged commit / queued proposal objects cannot cross the FFI boundary; the caller
        // keeps the original bytes and hands them back to `mls_merge_staged_commit`.
        ProcessedMessageContent::StagedCommitMessage(_) => (ProcessedKind::StagedCommit, message),
        ProcessedMessageContent::ProposalMessage(_)
        | ProcessedMessageContent::ExternalJoinProposalMessage(_) => {
            (ProcessedKind::Proposal, message)
        }
    };

    Ok(ProcessResult {
        state: snapshot_from_provider(&provider)?,
        kind,
        payload,
    })
}

/// Encrypt an application message in the group's current epoch (FR-004).
///
/// openmls refuses to encrypt while proposals are pending — that error surfaces as
/// [`MlsError::Operation`] rather than silently committing (FR-009).
#[uniffi::export]
pub fn mls_encrypt(
    state: Vec<u8>,
    group_id: Vec<u8>,
    plaintext: Vec<u8>,
) -> Result<EncryptResult, MlsError> {
    let provider = provider_from_snapshot(&state)?;
    let mut group = load_group(&provider, &group_id)?;
    let signer = signer_for(&provider, &group)?;

    let message = group
        .create_message(&provider, &signer, &plaintext)
        .map_err(op)?;

    Ok(EncryptResult {
        state: snapshot_from_provider(&provider)?,
        ciphertext: message_bytes(&message)?,
    })
}

/// Decrypt an application message. A non-application message is a deterministic error.
#[uniffi::export]
pub fn mls_decrypt(
    state: Vec<u8>,
    group_id: Vec<u8>,
    ciphertext: Vec<u8>,
) -> Result<DecryptResult, MlsError> {
    let provider = provider_from_snapshot(&state)?;
    let mut group = load_group(&provider, &group_id)?;

    let parsed = parse_message(&ciphertext)?;
    let protocol_message: ProtocolMessage =
        parsed
            .try_into_protocol_message()
            .map_err(|_| MlsError::MalformedMessage {
                msg: "not a protocol message".to_string(),
            })?;
    let processed = group
        .process_message(&provider, protocol_message)
        .map_err(op)?;

    match processed.into_content() {
        ProcessedMessageContent::ApplicationMessage(app) => Ok(DecryptResult {
            state: snapshot_from_provider(&provider)?,
            plaintext: app.into_bytes(),
        }),
        _ => Err(MlsError::MalformedMessage {
            msg: "not an application message".to_string(),
        }),
    }
}

/// Generate a KeyPackage (plus its ephemeral identity) into the snapshot (FR-010).
///
/// `last_resort = true` marks it reusable per RFC 9750 §5.1.
#[uniffi::export]
pub fn mls_generate_key_package(
    state: Vec<u8>,
    last_resort: bool,
) -> Result<KeyPackageResult, MlsError> {
    let provider = provider_from_snapshot(&state)?;
    let (signer, credential_with_key) = new_identity(&provider)?;

    let mut builder = KeyPackage::builder();
    if last_resort {
        builder = builder.mark_as_last_resort();
    }
    let bundle = builder
        .build(CIPHERSUITE, &provider, &signer, credential_with_key)
        .map_err(op)?;

    let key_package = MlsMessageOut::from(bundle.key_package().clone());

    Ok(KeyPackageResult {
        state: snapshot_from_provider(&provider)?,
        key_package: message_bytes(&key_package)?,
        identity: signer.public().to_vec(),
    })
}

/// Join a group from a Welcome produced by [`mls_add_members`].
///
/// Not a `GroupPort` verb (the domain port has no join method yet — TASK-67 pairing owns that
/// flow); it exists so the adapter can build genuine two-party groups, which is what the
/// roundtrip / forward-secrecy tests need.
#[uniffi::export]
pub fn mls_join_from_welcome(state: Vec<u8>, welcome: Vec<u8>) -> Result<JoinResult, MlsError> {
    let provider = provider_from_snapshot(&state)?;

    let welcome = match parse_message(&welcome)?.extract() {
        MlsMessageBodyIn::Welcome(w) => w,
        _ => {
            return Err(MlsError::MalformedMessage {
                msg: "not a Welcome".to_string(),
            })
        }
    };

    let staged = StagedWelcome::new_from_welcome(&provider, &join_config(), welcome, None)
        .map_err(op)?;
    let group = staged.into_group(&provider).map_err(op)?;

    Ok(JoinResult {
        state: snapshot_from_provider(&provider)?,
        group_id: group.group_id().as_slice().to_vec(),
        identity: own_identity(&group),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Two-party smoke test entirely inside Rust: create → add → join → encrypt → decrypt.
    #[test]
    fn two_party_group_encrypts_and_decrypts() {
        let alice = mls_create_group(Vec::new(), b"g1".to_vec()).expect("create");
        let bob_kp = mls_generate_key_package(Vec::new(), false).expect("key package");

        let added = mls_add_members(alice.state, b"g1".to_vec(), vec![bob_kp.key_package])
            .expect("add");
        let alice_state = mls_merge_pending_commit(added.state, b"g1".to_vec())
            .expect("merge")
            .state;

        let bob = mls_join_from_welcome(bob_kp.state, added.welcome.expect("welcome"))
            .expect("join");
        assert_eq!(bob.group_id, b"g1".to_vec());

        let encrypted = mls_encrypt(alice_state, b"g1".to_vec(), b"hello bob".to_vec())
            .expect("encrypt");
        let decrypted = mls_decrypt(bob.state, b"g1".to_vec(), encrypted.ciphertext)
            .expect("decrypt");
        assert_eq!(decrypted.plaintext, b"hello bob".to_vec());
    }

    #[test]
    fn double_create_is_error() {
        let first = mls_create_group(Vec::new(), b"g1".to_vec()).expect("create");
        assert!(matches!(
            mls_create_group(first.state, b"g1".to_vec()),
            Err(MlsError::GroupExists)
        ));
    }

    #[test]
    fn merge_without_pending_commit_is_error() {
        let created = mls_create_group(Vec::new(), b"g1".to_vec()).expect("create");
        assert!(matches!(
            mls_merge_pending_commit(created.state, b"g1".to_vec()),
            Err(MlsError::NoPendingCommit)
        ));
    }

    #[test]
    fn unknown_group_is_error() {
        assert!(matches!(
            mls_encrypt(Vec::new(), b"nope".to_vec(), b"x".to_vec()),
            Err(MlsError::UnknownGroup)
        ));
    }

    #[test]
    fn garbage_message_is_error_not_panic() {
        let created = mls_create_group(Vec::new(), b"g1".to_vec()).expect("create");
        assert!(matches!(
            mls_process_message(created.state, b"g1".to_vec(), vec![0xde, 0xad, 0xbe, 0xef]),
            Err(MlsError::MalformedMessage { .. })
        ));
    }

    #[test]
    fn remove_unknown_member_is_error() {
        let created = mls_create_group(Vec::new(), b"g1".to_vec()).expect("create");
        assert!(matches!(
            mls_remove_members(created.state, b"g1".to_vec(), vec![vec![9u8; 32]]),
            Err(MlsError::UnknownMember)
        ));
    }
}
