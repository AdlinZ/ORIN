package com.adlin.orin.modules.skill.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpEnvSecretRefTest {

    @Test
    void isSecretRef_validReference() {
        assertTrue(McpEnvSecretRef.isSecretRef("${secret:gsec_mcp_abc123}"));
        assertTrue(McpEnvSecretRef.isSecretRef("  ${secret:gsec_mcp_abc123}  "));
    }

    @Test
    void isSecretRef_rejectsEmbeddedOrMalformed() {
        assertFalse(McpEnvSecretRef.isSecretRef("prefix-${secret:abc}"));
        assertFalse(McpEnvSecretRef.isSecretRef("${secret:abc}-suffix"));
        assertFalse(McpEnvSecretRef.isSecretRef("${secret:}"));
        assertFalse(McpEnvSecretRef.isSecretRef("${secret:has space}"));
        assertFalse(McpEnvSecretRef.isSecretRef("ghp_plainvalue"));
        assertFalse(McpEnvSecretRef.isSecretRef(null));
    }

    @Test
    void extractSecretId_returnsIdOrNull() {
        assertEquals("gsec_mcp_abc123", McpEnvSecretRef.extractSecretId("${secret:gsec_mcp_abc123}"));
        assertNull(McpEnvSecretRef.extractSecretId("ghp_plainvalue"));
        assertNull(McpEnvSecretRef.extractSecretId(null));
    }

    @Test
    void isSensitiveKey_matchesSuffixCaseInsensitive() {
        assertTrue(McpEnvSecretRef.isSensitiveKey("GITHUB_PERSONAL_ACCESS_TOKEN"));
        assertTrue(McpEnvSecretRef.isSensitiveKey("api_key"));
        assertTrue(McpEnvSecretRef.isSensitiveKey("Client_Secret"));
        assertFalse(McpEnvSecretRef.isSensitiveKey("WORKDIR"));
        assertFalse(McpEnvSecretRef.isSensitiveKey("PATH"));
        assertFalse(McpEnvSecretRef.isSensitiveKey(null));
    }
}
