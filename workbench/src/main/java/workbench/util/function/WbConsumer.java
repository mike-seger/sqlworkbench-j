package workbench.util.function;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Represents an operation that accepts a single input argument and returns no result.
 * <p>
 * <p>
 * This is a <a href="package-summary.html">functional interface</a>
 * whose functional method is {@link #accept(Object)}.
 * <p>
 * This is a copy of {@link Consumer} that supports checked exceptions
 *
 * @param <T>
 *
 * @see Consumer
 * @author Andreas Krist
 */
@FunctionalInterface
public interface WbConsumer<T>
{
  /**
   * Performs this operation on the given argument.
   *
   * @param t the input argument
   */
  void accept(T t)
    throws Exception;

  /**
   * Returns a composed {@code Consumer} that performs, in sequence, this
   * operation followed by the {@code after} operation. If performing either
   * operation throws an exception, it is relayed to the caller of the
   * composed operation. If performing this operation throws an exception,
   * the {@code after} operation will not be performed.
   *
   * @param after the operation to perform after this operation
   *
   * @return a composed {@code Consumer} that performs in sequence this
   *         operation followed by the {@code after} operation
   *
   * @throws NullPointerException if {@code after} is null
   */
  default WbConsumer<T> andThen(WbConsumer<? super T> after)
  {
    Objects.requireNonNull(after);
    return (T t) ->
    {
      accept(t);
      after.accept(t);
    };
  }
}
