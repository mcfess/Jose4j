/*
 * Copyright 2012-2015 Brian Campbell
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jose4j.jwt.consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwx.JsonWebStructure;
import org.jose4j.keys.X509Util;
import org.jose4j.lang.UncheckedJoseException;
import org.jose4j.lang.UnresolvableKeyException;

import java.security.Key;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jose4j.jwx.HeaderParameterNames.X509_CERTIFICATE_SHA256_THUMBPRINT;
import static org.jose4j.jwx.HeaderParameterNames.X509_CERTIFICATE_THUMBPRINT;

/**
 *
 */
public class X509VerificationKeyResolver implements VerificationKeyResolver
{
    final static Log log = LogFactory.getLog(X509VerificationKeyResolver.class);

    Map<String,X509Certificate> x5tMap;
    Map<String,X509Certificate> x5tS256Map;

    public X509VerificationKeyResolver(List<X509Certificate> certificates)
    {
        x5tMap = new HashMap<>();
        x5tS256Map = new HashMap<>();

        for (X509Certificate cert : certificates)
        {
            try
            {
                String x5t = X509Util.x5t(cert);
                x5tMap.put(x5t, cert);

                String x5tS256 = X509Util.x5tS256(cert);
                x5tS256Map.put(x5tS256, cert);
            }
            catch (UncheckedJoseException e)
            {
                log.warn("Unable to get certificate thumbprint.", e);
            }
        }
    }

    public X509VerificationKeyResolver(X509Certificate... certificates)
    {
        this(Arrays.asList(certificates));
    }

    @Override
    public Key resolveKey(JsonWebSignature jws, List<JsonWebStructure> nestingContext) throws UnresolvableKeyException
    {
        String x5t = jws.getHeader(X509_CERTIFICATE_THUMBPRINT);
        String x5tS256 = jws.getHeader(X509_CERTIFICATE_SHA256_THUMBPRINT);

        if (x5t == null && x5tS256 == null)
        {
            throw new UnresolvableKeyException("Neither the " + X509_CERTIFICATE_THUMBPRINT + " header nor the " + X509_CERTIFICATE_SHA256_THUMBPRINT + " header are present in the JWS.");
        }

        X509Certificate x509Certificate = x5tMap.get(x5t);
        if (x509Certificate == null)
        {
            x509Certificate = x5tS256Map.get(x5tS256);
        }

        if (x509Certificate == null)
        {
            StringBuilder sb = new StringBuilder();

            sb.append("The X.509 Certificate Thumbprint header(s) in the JWS do not identify any of the provided Certificates -");
            if (x5t != null)
            {
                sb.append(" ").append(X509_CERTIFICATE_THUMBPRINT).append("=").append(x5t);
                sb.append(" vs. SHA-1 thumbs:").append(x5tMap.keySet());
            }

            if (x5tS256 != null)
            {
                sb.append(" ").append(X509_CERTIFICATE_SHA256_THUMBPRINT).append("=").append(x5tS256);
                sb.append(" vs. SHA-256 thumbs:").append(x5tS256Map.keySet());
            }

            sb.append(".");
            throw new UnresolvableKeyException(sb.toString());
        }

        return x509Certificate.getPublicKey();
    }
}