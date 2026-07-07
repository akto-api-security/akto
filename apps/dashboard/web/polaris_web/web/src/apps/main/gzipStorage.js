import pako from "pako"; // Gzip Compression

// Factory function to create Custom Storage with Gzip Compression
export const createGzipStorage = (storage) => ({
    getItem: (name) => {
        const compressedData = storage.getItem(name);
        if (!compressedData) return null;

        try {
            // Try to decode base64 & Gunzip (decompress)
            const binaryData = atob(compressedData);
            const uint8Array = new Uint8Array(binaryData.length);
            for (let i = 0; i < binaryData.length; i++) {
                uint8Array[i] = binaryData.charCodeAt(i);
            }
            const decompressed = pako.inflate(uint8Array, { to: "string" });
            return JSON.parse(decompressed);
        } catch (error) {
            // Fallback: Try to parse as plain JSON (for backward compatibility with old uncompressed data)
            try {
                const parsed = JSON.parse(compressedData);
                // If successful, re-save it in compressed format
                storage.setItem(name, btoa(Array.from(pako.deflate(compressedData, { level: 9 }))
                    .map((byte) => String.fromCharCode(byte))
                    .join("")));
                return parsed;
            } catch (fallbackError) {
                console.error("Error reading state (tried both compressed and uncompressed):", error);
                return null;
            }
        }
    },
    setItem: (name, value) => {
        try {
            // Stringify, Gzip compress, then convert to Base64
            const jsonString = JSON.stringify(value);
            const compressed = pako.deflate(jsonString, { level: 9 });
            const binaryString = Array.from(compressed)
                .map((byte) => String.fromCharCode(byte))
                .join("");
            const base64Encoded = btoa(binaryString);
            storage.setItem(name, base64Encoded);
        } catch (error) {
            console.error("Error compressing state:", error);
        }
    },
    removeItem: (name) => storage.removeItem(name),
});
