package online.alldare.auth.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public endpoint that exposes the JWKS (JSON Web Key Set) for token verification.
 * This is used by the gateway and other services to verify JWT signatures.
 */
@RestController
public class JwksController {

    private final JWKSource<SecurityContext> jwkSource;

    public JwksController(JWKSource<SecurityContext> jwkSource) {
        this.jwkSource = jwkSource;
    }

    /**
     * GET /oauth2/jwks
     * Returns the public keys in JWKS format for external JWT verification.
     * The gateway uses this endpoint to fetch keys and verify incoming tokens.
     */
    @GetMapping("/oauth2/jwks")
    public Map<String, Object> getJwks() throws Exception {
        JWKSet jwkSet = new JWKSet(jwkSource.get(null, null));
        return jwkSet.toJSONObject();
    }
}
