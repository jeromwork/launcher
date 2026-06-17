# Frozen KeyBlob fixtures (F-CRYPTO 1.0.0)

These JSON files are **frozen** at F-CRYPTO 1.0.0 release. They MUST NEVER be modified
after that point. Per CLAUDE.md rule 5 (wire-format versioning) and contracts/key-blob-v1.md
("Backward-compat read test contract"), every future minor release must successfully
parse these blobs.

If the schema needs a breaking change, bump to schemaVersion=2 and add new
`v2-sample.json` fixtures **alongside** these — do not edit existing files.

`wrappedKey` / `iv` bytes are dummy values intended for parsing tests only;
they are **not** real encrypted material and cannot be decrypted.
