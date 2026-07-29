package com.github.mkram17.bazaarutils.utils.web;

import com.google.gson.Gson;

/**
 * The Gson used for everything on the wire.
 *
 * <p>Deliberately its own instance, and <strong>not</strong> {@code DataStorage}'s. That one has an
 * {@code ItemStack} codec adapter registered so it can persist orders to disk — the exact adapter
 * that would quietly serialize full item NBT into a request body if a model object ever reached it.
 * Keeping the transport on a plain Gson means the only thing that can go out is what the DTOs
 * declare.</p>
 *
 * <p>Stock defaults happen to match the website's schema, so there is nothing to configure:</p>
 * <ul>
 *   <li>enums serialize as {@link Enum#name()}, not {@code toString()} — which matters, because
 *       {@code TransactionType.Side} renders itself as "Buy"/"Sell" for chat while the wire
 *       contract is {@code BUY}/{@code SELL};</li>
 *   <li>null fields are omitted rather than emitted as {@code null}, so an optional field the mod
 *       has no value for simply does not appear.</li>
 * </ul>
 *
 * <p>Responses deserialize with absent fields left null (and absent primitives left zero), so a
 * parsed response record is not evidence that the server actually sent those fields — callers
 * still have to check.</p>
 */
final class WebJson {
    static final Gson GSON = new Gson();

    private WebJson() {}
}
