# Secure asset transport

Deploy this emulator together with the updated client, including
`public/configuration/bootstrap.js`. `/nitro-sec/file` now sends
`application/vnd.nitro.secure-gzip` with `X-Nitro-Sec: 1`:

1. Read the bounded source file and gzip it once.
2. Cache the compressed bytes by resolved file path, modification time and size.
3. Encrypt those compressed bytes with the existing session AES-GCM key.
4. Send the binary IV followed by the ciphertext and authentication tag.

The client authenticates/decrypts the binary response, then uses the native
`DecompressionStream('gzip')`. Asset responses no longer use hexadecimal
encoding. The secure API protocol is separate and retains its existing
encoding; its byte-to-hex conversion now uses `HexFormat`.

Asset GETs carry the public ECDH key in the URL's `key` parameter rather than
a custom request header, avoiding a CORS preflight for each asset. This key
is public material, not an access token. API requests keep their own headers.

The compressed cache avoids repeated gzip and source JSON parsing. Concurrent
cold requests for the same path share cache creation. An edited source replaces
the cached payload when its modification time or size changes. The original
source file is not rewritten or minified.

The default gamedata source limit is 64 MiB, configurable with
`nitro.secure.gamedata.max_file_bytes`. Existing explicit settings take
precedence. This limit applies before file read and before serving a cache hit;
it accommodates the current public custom furnidata (43,063,516 source bytes).
The config-file limit remains 2 MiB.

`Server-Timing` reports `key`, `asset` and `encrypt` durations in milliseconds.
The `asset` phase includes file checks and a cold read/gzip when necessary.
These measurements cover handler work, not proxy queues or network latency.

## Measured source payloads on 15 October 2026

Public JSON files fetched from the configured gamedata CDN:

| File | Original bytes | Gzip bytes before AES-GCM |
| --- | ---: | ---: |
| FurnitureData.json | 20,559,846 | 797,634 |
| gamedata_custom/FurnitureData_custom.json | 43,063,516 | 1,130,034 |

Measurements above used Node's default gzip on the actual downloaded sources.
AES-GCM adds 28 bytes per response (12-byte IV and 16-byte tag); HTTP headers
and any proxy processing are additional. A second check through the actual
Java HTTP handler produced 810,278 and 1,139,768 encrypted body bytes,
respectively: about 1.95 MB together. This is a payload measurement, not a
production page-load timing or guaranteed compression ratio for every asset.

In the same local Java check, cold handler preparation took approximately
441 ms (base) and 777 ms (custom). With the compressed entries cached,
preparation took approximately 15 ms and 20 ms. Both cold and warm responses
were decrypted, decompressed and checked byte-for-byte against the sources.
These local timings exclude the browser, TLS, Cloudflare and internet transfer.

The two downloaded sets have disjoint furniture IDs; neither file can be
discarded as a duplicate of the other. Five repeated floor-item IDs were found
inside the custom file; the authoritative asset was left untouched.

## Verification

The transport tests exercise HTTP responses, gzip before encryption, Unicode,
source edits, cached responses and the configured size cap. They write a
test-only fixture to `Emulator/target/secure-asset-transport-fixture.json` for
cross-checking the Java HTTP output against the client decoder. Its key is
ephemeral test data, unrelated to a running hotel.

After deployment, compare a cold and warm `/hotel` load in DevTools. Check the
new response content type, transferred bytes, `Server-Timing`, TTFB and content
download. Existing timestamp versioning and per-page response sharing remain;
HTTP `no-store` still prevents browser reuse between page loads.

Local checks on 15 October 2026: JDK 25 compilation and `mvn package`
completed with 2,071 unit tests reported, zero failures/errors and 36 skipped.
Spotless checks passed for the five changed Java files; SpotBugs reported
zero findings and zero analysis errors.
The five secure transport/encoding/contract tests pass, including requests
using the public key query parameter. The Java response fixture was also
decoded by the actual client TypeScript helper with an exact content match.
Client production build, type check and 20 focused startup/cache/transport
tests passed. The broader client suite still reports 11 failures in unchanged
layout and mock tests. These results do not claim a hosted CI or production run.
