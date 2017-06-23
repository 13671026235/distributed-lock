/*
 * Copyright (c)  2017 Alen Turković <alturkovic@gmail.com>
 *
 * Permission to use, copy, modify, and distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package com.github.alturkovic.lock;

import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Data
public abstract class AbstractLock implements Lock {

    @Override
    public String acquire(final List<String> keys, final String storeId,
                          final TimeUnit expirationUnit, final long expiration,
                          final long retryMillis, final TimeUnit timeoutUnit, final long timeout) {
        if (timeout <= 0) {
            return acquireLock(keys, storeId, expirationUnit, expiration);
        }

        long currentTimeout = timeout;

        while (currentTimeout >= 0) {
            final String token = acquireLock(keys, storeId, expirationUnit, expiration);

            if (!StringUtils.isEmpty(token)) {
                // token was acquired, method is considered locked
                return token;
            }

            // if token was not acquired, wait and try again until timeout
            currentTimeout -= retryMillis;

            try {
                Thread.sleep(retryMillis);
            } catch (final InterruptedException e) {
                // Do nothing...
            }
        }

        return null;
    }

    public abstract String acquireLock(List<String> keys, String storeId, TimeUnit expirationUnit, long expiration);
}
