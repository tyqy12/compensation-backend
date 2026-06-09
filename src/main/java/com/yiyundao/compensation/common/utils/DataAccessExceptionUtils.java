package com.yiyundao.compensation.common.utils;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

public final class DataAccessExceptionUtils {

    private DataAccessExceptionUtils() {
    }

    public static boolean isResourceFailure(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof CannotGetJdbcConnectionException
                    || current instanceof DataAccessResourceFailureException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
