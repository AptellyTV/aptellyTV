package app.aptelly.tv.update;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.util.Base64;

import org.junit.Test;

public final class AptellyUpdateVerifierTest {
    private static final String PAYLOAD =
            "eyJwYWNrYWdlTmFtZSI6ImFwcC5hcHRlbGx5LnR2IiwidmVyc2lvbkNvZGUiOjQ4LCJ2ZXJzaW9uTmFtZSI6IjAuMTIuMjUiLCJtaW5TZGsiOjI2LCJhcGtVcmwiOiJodHRwczovL2Rvd25sb2FkLmV4YW1wbGUuY29tL0FwdGVsbHktMC4xMi4yNS1yZWxlYXNlLmFwayIsInNpemVCeXRlcyI6MTIzNCwic2hhMjU2IjoiYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYSIsInNpZ25pbmdDZXJ0aWZpY2F0ZVNoYTI1NiI6ImU5ZWM0MzE5OWFiN2M5ODcyMzI4MDM3YWRhOWY1Y2UwOGQzNjdkZjI1YWVmODczMmUwMTViMDZlYTI4YmUzNzUiLCJtYW5kYXRvcnkiOmZhbHNlfQo=";
    private static final String SIGNATURE =
            "DpWSK3KxcdYNyqHY7MZqUQ52marU80MOfh3+56FnOyR5DiT7tRY/Rj400z+RTcB83bwP2DWs94YpbEqhALNembK02N3/A4czknerM3X4v1gZ0Nfr6W/RmSyojBTZeMziRhgw0Vrq7gR9kDIqUrupMaO0AIiWSsXbRPzRioRaLaKlVmVPljQa9BUZSej98r84cLZqR9CmOUDEj9o6TwbJSrLE9CrjYtgPxAy3G5g4Yd9k3uXpDLrEROG+gQ/2kDJrJ17zBBudqUVLtmO9wqH5Y1VbryFK8U2SQt7pl0AHmq2KSIE4H298RXeuURa8cmP0CGp/YGALveM5oExTg7YxBM+TKDpnUx4xUOu58R2mG0A0O9MWy0eZkZEdQz0pFYm7Ql1t8j5e6UVOXGiCnJYs66wbsHKVTdWYeMW5FpSvPDAObSWuwTgXC+OhCzhZmdMIqWvUNeWYBGP047aLP0PHkijb/urCLd+DF83BCDcF8cE44RuKPbWuRenPU6Fjx6WTPVCu5qNdGuRPNAFO2g4dv7wY6vfOdwE1t5hnJ4HwH9H7LFnO2snF7shzwunMGhToc0S5d2ZuOFU7Oj9B+Nr6Q1d1GjmQy9ZNniDb/qTtOXmvE4qZ7/UYue/bR/bDeu6GW7M0i7kC775T86yt5XkZzfKYnzWup3Rksel5J1Z2s94=";

    @Test
    public void acceptsOfflineReleaseSignatureAndRejectsTampering() throws Exception {
        byte[] payload = Base64.getDecoder().decode(PAYLOAD);
        byte[] signature = Base64.getDecoder().decode(SIGNATURE);
        assertTrue(AptellyUpdateVerifier.verifySignature(payload, signature));
        payload[0] ^= 1;
        assertFalse(AptellyUpdateVerifier.verifySignature(payload, signature));
    }

    @Test
    public void allowsOnlyHttpsGitHubReleaseRedirectsOrSameHost() throws Exception {
        URL github = new URL("https://github.com/AptellyTV/aptellyTV/releases/download/v1/app.apk");
        URL releaseAsset = new URL("https://release-assets.githubusercontent.com/github-production-release-asset/app.apk");
        assertTrue(AptellyUpdateClient.isRedirectAllowed(github, releaseAsset));
        assertTrue(AptellyUpdateClient.isRedirectAllowed(
                new URL("https://downloads.example.com/app.apk"),
                new URL("https://downloads.example.com/files/app.apk")
        ));
        assertFalse(AptellyUpdateClient.isRedirectAllowed(
                github,
                new URL("https://downloads.example.com/app.apk")
        ));
        assertFalse(AptellyUpdateClient.isRedirectAllowed(
                github,
                new URL("http://release-assets.githubusercontent.com/app.apk")
        ));
    }
}
