package com.zodiakk.authservice.common.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaEventConstants {

    public static final String AGGREGATE_TYPE_EMPLOYEE_ACCOUNT = "EMPLOYEE_ACCOUNT";

    public static final String USER_CREATED = "USER_CREATED";

    public static final String USER_DELETED = "USER_DELETED";

    public static final String EMPLOYEE_ACCOUNT_UNLINKED = "EMPLOYEE_ACCOUNT_UNLINKED";

    public static final int EVENT_VERSION_V1 = 1;

    public static final String DLT_SUFFIX = ".DLT";
}
