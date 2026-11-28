package com.github.mkram17.bazaarutils.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal regex dispatch utility useful for chat message parsing.
 *
 * All patterns must be compiled as {@code static final} constants at the call site
 * so compilation cost is paid once at class load, not per message.
 *
 * Usage:
 * <pre>
 *   RegexSwitch.when()
 *       .on(BUY_CANCEL,  m -> handleBuyCancel(m))
 *       .on(SELL_CANCEL, m -> handleSellCancel(m))
 *       .against(message.getString());
 * </pre>
 *
 * First matching pattern wins; subsequent patterns are not evaluated.
 */
public final class RegexSwitch {

    private RegexSwitch() {}

    public static Switch when() { return new Switch(); }

    public static final class Switch {

        private record Case(Pattern pattern, Consumer<Matcher> action) {}

        private final List<Case> cases = new ArrayList<>();

        /**
         * Register a pattern and the action to run if it matches.
         * Patterns are tested in registration order; first match wins.
         */
        public Switch on(Pattern pattern, Consumer<Matcher> action) {
            cases.add(new Case(pattern, action));
            return this;
        }

        /**
         * Run against {@code input}. Uses {@link Matcher#find()} so patterns do not
         * need to anchor to the full string — partial matches work.
         *
         * @return true if any pattern matched
         */
        public boolean against(String input) {
            for (Case c : cases) {
                Matcher matcher = c.pattern().matcher(input);

                if (matcher.find()) {
                    c.action().accept(matcher);

                    return true;
                }
            }
            return false;
        }
    }
}