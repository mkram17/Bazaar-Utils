package com.github.mkram17.bazaarutils.utils;

/**
 * Represents the outcome of a single handler's execution within an ordered pipeline.
 *
 * <p>A pipeline passes a shared context (e.g. an item stack, a screen context) through a sequence
 * of handlers. Each handler declares two things via its result:
 * <ol>
 *   <li><b>acted</b> — whether this handler made a meaningful change to the context.</li>
 *   <li><b>propagate</b> — whether subsequent handlers in the pipeline should still run.</li>
 * </ol>
 *
 * <p>Use the four constants for all standard cases. Construct directly only when you need
 * to express a non-standard combination (unusual in practice).
 *
 * <table>
 *   <tr><th>Constant</th>   <th>acted</th><th>propagate</th><th>Meaning</th></tr>
 *   <tr><td>UNMODIFIED</td> <td>false</td><td>true</td>  <td>Pass-through; did nothing, pipeline continues.</td></tr>
 *   <tr><td>HANDLED</td>    <td>true</td> <td>true</td>  <td>Made a change; pipeline may still continue.</td></tr>
 *   <tr><td>CONSUMED</td>   <td>true</td> <td>false</td> <td>Made a change and halts the pipeline.</td></tr>
 *   <tr><td>CANCELLED</td>  <td>false</td><td>false</td> <td>Vetoes further execution without acting.</td></tr>
 * </table>
 */
public record Result(boolean acted, boolean propagate) {

    /** This handler did nothing; pass context to the next handler unchanged. */
    public static final Result UNMODIFIED = new Result(false, true);

    /** This handler acted on the context; subsequent handlers may still run. */
    public static final Result HANDLED = new Result(true,  true);

    /** This handler acted on the context and no further handlers should run. */
    public static final Result CONSUMED = new Result(true,  false);

    /** No action was taken, but the pipeline is halted (e.g. a veto). */
    public static final Result CANCELLED = new Result(false, false);
}