//! In-memory MLS storage snapshot (TASK-124, T003).
//!
//! The FFI boundary is **stateless**: every verb in [`crate::mls`] receives a serialized snapshot
//! of the whole openmls `StorageProvider` and returns an updated one. Kotlin owns the bytes; Rust
//! owns no long-lived state (spec task-124 §Architecture, contracts/mls-ffi-surface.md).
//!
//! **Deviation from plan.md §T003 (deliberate, rule 4 MVA).** The plan sketched a hand-written
//! `InMemoryStorageProvider`. We reuse `openmls_rust_crypto::MemoryStorage` instead — it already
//! implements the ~40-method `StorageProvider<CURRENT_VERSION>` trait and exposes its backing
//! `HashMap<Vec<u8>, Vec<u8>>` as a public field. Re-implementing the trait would be a
//! reimplementation of imported crypto plumbing with a fresh bug surface and zero gain. What this
//! module owns is exactly the part the upstream crate does NOT give us on stable features: the
//! snapshot codec (upstream's own `serialize`/`deserialize` sit behind the `test-utils` feature).
//!
//! **Snapshot format** — internal, in-memory, NOT a persisted wire format, therefore deliberately
//! unversioned (spec Clarification #3; `wire-format.md` discipline starts at TASK-125/SQLCipher):
//!
//! ```text
//! u64 BE  entry_count
//! repeat entry_count times:
//!   u64 BE  key_len
//!   u64 BE  value_len
//!   key_len bytes
//!   value_len bytes
//! ```

use std::collections::HashMap;

use openmls_rust_crypto::OpenMlsRustCrypto;
use openmls_traits::OpenMlsProvider;

use crate::mls::MlsError;

/// Rebuild a provider (crypto + rand + storage) from a snapshot.
///
/// An empty snapshot yields an empty provider — that is the "fresh device" case
/// (`create_group` / `generate_key_package` on first call).
pub(crate) fn provider_from_snapshot(state: &[u8]) -> Result<OpenMlsRustCrypto, MlsError> {
    let provider = OpenMlsRustCrypto::default();
    if state.is_empty() {
        return Ok(provider);
    }
    let entries = decode(state)?;
    {
        let mut values = provider
            .storage()
            .values
            .write()
            .map_err(|_| MlsError::Storage {
                msg: "storage lock poisoned".to_string(),
            })?;
        values.extend(entries);
    }
    Ok(provider)
}

/// Serialize the provider's whole storage back into a snapshot.
pub(crate) fn snapshot_from_provider(provider: &OpenMlsRustCrypto) -> Result<Vec<u8>, MlsError> {
    let values = provider
        .storage()
        .values
        .read()
        .map_err(|_| MlsError::Storage {
            msg: "storage lock poisoned".to_string(),
        })?;
    Ok(encode(&values))
}

fn encode(values: &HashMap<Vec<u8>, Vec<u8>>) -> Vec<u8> {
    let mut out = Vec::with_capacity(8 + values.len() * 32);
    out.extend_from_slice(&(values.len() as u64).to_be_bytes());
    for (k, v) in values.iter() {
        out.extend_from_slice(&(k.len() as u64).to_be_bytes());
        out.extend_from_slice(&(v.len() as u64).to_be_bytes());
        out.extend_from_slice(k);
        out.extend_from_slice(v);
    }
    out
}

fn decode(bytes: &[u8]) -> Result<HashMap<Vec<u8>, Vec<u8>>, MlsError> {
    let mut cursor = 0usize;
    let count = read_u64(bytes, &mut cursor)? as usize;
    let mut map = HashMap::with_capacity(count);
    for _ in 0..count {
        let key_len = read_u64(bytes, &mut cursor)? as usize;
        let value_len = read_u64(bytes, &mut cursor)? as usize;
        let key = read_bytes(bytes, &mut cursor, key_len)?;
        let value = read_bytes(bytes, &mut cursor, value_len)?;
        map.insert(key, value);
    }
    Ok(map)
}

fn read_u64(bytes: &[u8], cursor: &mut usize) -> Result<u64, MlsError> {
    let end = cursor.checked_add(8).ok_or_else(truncated)?;
    let slice = bytes.get(*cursor..end).ok_or_else(truncated)?;
    let mut buf = [0u8; 8];
    buf.copy_from_slice(slice);
    *cursor = end;
    Ok(u64::from_be_bytes(buf))
}

fn read_bytes(bytes: &[u8], cursor: &mut usize, len: usize) -> Result<Vec<u8>, MlsError> {
    let end = cursor.checked_add(len).ok_or_else(truncated)?;
    let slice = bytes.get(*cursor..end).ok_or_else(truncated)?;
    *cursor = end;
    Ok(slice.to_vec())
}

fn truncated() -> MlsError {
    MlsError::Storage {
        msg: "snapshot truncated or malformed".to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn empty_snapshot_yields_empty_provider() {
        let provider = provider_from_snapshot(&[]).expect("empty snapshot loads");
        assert!(provider.storage().values.read().unwrap().is_empty());
    }

    #[test]
    fn snapshot_roundtrips_through_provider() {
        let provider = OpenMlsRustCrypto::default();
        {
            let mut values = provider.storage().values.write().unwrap();
            values.insert(b"k1".to_vec(), b"v1".to_vec());
            values.insert(b"".to_vec(), b"empty-key".to_vec());
            values.insert(b"k3".to_vec(), Vec::new());
        }

        let snapshot = snapshot_from_provider(&provider).expect("encode");
        let restored = provider_from_snapshot(&snapshot).expect("decode");

        let original = provider.storage().values.read().unwrap().clone();
        let roundtripped = restored.storage().values.read().unwrap().clone();
        assert_eq!(original, roundtripped);
    }

    #[test]
    fn malformed_snapshot_is_error_not_panic() {
        assert!(provider_from_snapshot(&[0u8, 1, 2]).is_err());
        // Declares one entry, then ends.
        let mut bytes = 1u64.to_be_bytes().to_vec();
        bytes.extend_from_slice(&4u64.to_be_bytes());
        assert!(provider_from_snapshot(&bytes).is_err());
    }
}
