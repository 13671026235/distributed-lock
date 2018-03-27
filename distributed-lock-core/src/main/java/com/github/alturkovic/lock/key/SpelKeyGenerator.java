/*
 * MIT License
 *
 * Copyright (c) 2018 Alen Turkovic
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.github.alturkovic.lock.key;

import com.github.alturkovic.lock.exception.EvaluationConvertException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.Data;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Data
public class SpelKeyGenerator implements KeyGenerator {

  private final Map<Class<?>, Function<Object, String>> converterMap;

  public SpelKeyGenerator() {
    converterMap = new HashMap<>();

    final Function<Object, String> toStringFunction = Object::toString;
    converterMap.put(Boolean.class, toStringFunction);
    converterMap.put(Byte.class, toStringFunction);
    converterMap.put(Character.class, toStringFunction);
    converterMap.put(Double.class, toStringFunction);
    converterMap.put(Float.class, toStringFunction);
    converterMap.put(Integer.class, toStringFunction);
    converterMap.put(Long.class, toStringFunction);
    converterMap.put(Short.class, toStringFunction);
    converterMap.put(String.class, toStringFunction);
  }

  @Override
  public List<String> resolveKeys(final String lockKeyPrefix, final String expression, final String contextVariableName, final JoinPoint joinPoint) {
    final List<String> keys = evaluateExpression(expression, contextVariableName, joinPoint);

    if (StringUtils.isEmpty(lockKeyPrefix)) {
      return keys;
    }

    return keys.stream().map(key -> formatKey(lockKeyPrefix, key)).collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  public <T> void registerConverter(final Class<T> clazz, final Function<T, String> converter) {
    converterMap.put(clazz, (Function<Object, String>) converter);
  }

  private List<String> evaluateExpression(final String expression, final String contextVariableName, final JoinPoint joinPoint) {
    final StandardEvaluationContext context = new StandardEvaluationContext(joinPoint.getTarget());

    final Object[] args = joinPoint.getArgs();
    if (args != null && args.length > 0) {
      IntStream.range(0, args.length).forEach(idx -> context.setVariable(contextVariableName + idx, args[idx]));
    }

    final Signature signature = joinPoint.getSignature();
    if (signature instanceof MethodSignature) {
      context.setVariable("executionPath", joinPoint.getTarget().getClass().getCanonicalName() + "." + ((MethodSignature) signature).getMethod().getName());
    }

    final SpelExpressionParser parser = new SpelExpressionParser();
    final Expression sExpression = parser.parseExpression(expression);

    final Object expressionValue = sExpression.getValue(context);
    return convertResultToList(expressionValue);
  }

  private String formatKey(final String lockKeyPrefix, final String key) {
    if (StringUtils.isEmpty(key)) {
      return null;
    }

    if (StringUtils.isEmpty(lockKeyPrefix)) {
      return key;
    }

    return lockKeyPrefix + key;
  }

  @SuppressWarnings("unchecked")
  protected List<String> convertResultToList(final Object expressionValue) {
    final List<String> list;

    if (expressionValue == null) {
      throw new EvaluationConvertException("Expression evaluated in a null list");
    }

    final Function<Object, String> converterFunction = converterMap.get(expressionValue.getClass());

    if (converterFunction != null) {
      list = Collections.singletonList(converterFunction.apply(expressionValue));
    } else if (expressionValue instanceof Collection) {
      list = ((Collection<Object>) expressionValue).stream().map(o -> {
        final Function<Object, String> elementConvertFunction = converterMap.get(o.getClass());
        if (elementConvertFunction == null) {
          throw new EvaluationConvertException(String.format("Expression evaluated in a list, but element %s has no registered converter", o));
        }
        return elementConvertFunction.apply(o);
      }).collect(Collectors.toList());
    } else {
      throw new EvaluationConvertException(String.format("Expression evaluated in %s that has no registered converter", expressionValue));
    }

    if (CollectionUtils.isEmpty(list)) {
      throw new EvaluationConvertException("Expression evaluated in an empty list");
    }

    return list;
  }
}
