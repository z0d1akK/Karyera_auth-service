package com.zodiakk.authservice.common.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SecurityConstants {

    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-Key";

    public static final String INTERNAL_SERVICE_AUTHORITY = "ROLE_INTERNAL_SERVICE";

    public static final String BEARER = "Bearer";

    public static final String BEARER_PREFIX = BEARER + " ";

    public static final String ROLE_PREFIX = "ROLE_";
}
