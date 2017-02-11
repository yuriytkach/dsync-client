/*
 * Copyright (c) 2017 Yuriy Tkach
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.yet.dsync.exception;

public class DSyncClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DSyncClientException(final String message) {
        super(message);
    }

    public DSyncClientException(final Throwable cause) {
        super(cause);
    }

    public DSyncClientException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public DSyncClientException(final String message, final Throwable cause,
                                final boolean enableSuppression, final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
