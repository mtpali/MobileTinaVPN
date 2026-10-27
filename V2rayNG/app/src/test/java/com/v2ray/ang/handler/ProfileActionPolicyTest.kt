package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.SubscriptionItem
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProfileActionPolicyTest {
    @Test fun rawSourcesCannotBeRevealedEvenWithWhitespaceAndCaseChanges() {
        for (source in listOf("https://raw.githubusercontent.com/owner/repo/main/sub", "  HTTPS://RAW.GITHUBUSERCONTENT.COM/x  ", "\uFEFF  https://raw.githubusercontent.com/x")) {
            assertFalse(ProfileActionPolicy.allows(SubscriptionItem(url = source)))
        }
    }
    @Test fun hashPrefixesProtectBothUrlAndTitle() {
        assertFalse(ProfileActionPolicy.allows(SubscriptionItem(url = "  #secret")))
        assertFalse(ProfileActionPolicy.allows(SubscriptionItem(remarks = " #private", url = "https://example.com/sub")))
    }
    @Test fun normalSubscriptionsAndLocalGroupsRemainEditable() {
        assertTrue(ProfileActionPolicy.allows(SubscriptionItem(url = "https://example.com/sub")))
        assertTrue(ProfileActionPolicy.allows(SubscriptionItem(remarks = "Local")))
    }
    @Test fun ordinaryFragmentsAreNotMistakenForProtectedPrefixes() {
        assertTrue(ProfileActionPolicy.allows(SubscriptionItem(url = "https://example.com/sub#name", remarks = "Plan #2")))
    }
    @Test fun brandedTitleNormalizationCannotEraseAnExistingRestriction() {
        val item = SubscriptionItem(remarks = "#private", url = "https://example.com/sub")
        ProfileActionPolicy.seal(item)
        item.remarks = "Brand"
        ProfileActionPolicy.seal(item)
        assertFalse(ProfileActionPolicy.allows(item.copy()))
    }
    @Test fun persistedRestrictionsFailClosedAfterAnOldEditorChangesTheUrl() {
        assertFalse(ProfileActionPolicy.allows(SubscriptionItem(url = "https://example.com", actionRestricted = true)))
    }
}
