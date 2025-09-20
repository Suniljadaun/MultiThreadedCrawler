package com.sunil.finintel.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestHasherTest {

    @Test
    void matchesKnownSha256Value() {
        // Standard SHA-256 test vector for "abc"
        assertThat(RequestHasher.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void isDeterministicAnd64HexChars() {
        String first = RequestHasher.sha256Hex("order|1|ACME");
        assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(RequestHasher.sha256Hex("order|1|ACME")).isEqualTo(first);
        assertThat(RequestHasher.sha256Hex("order|1|ACMF")).isNotEqualTo(first);
    }
}
