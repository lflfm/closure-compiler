/*
 * Copyright 2018 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.TemplateTypeMap;
import com.google.javascript.rhino.jstype.UnionTypeBuilder;

/**
 * Models different Javascript Promise-related operations
 *
 * @author lharker@google.com (Laura Harker)
 */
final class Promises {

  private Promises() {}

  /**
   * If this object is known to be an IThenable, returns the type it resolves to.
   *
   * <p>Returns unknown otherwise.
   *
   * <p>(This is different from {@code getResolvedType}, which will attempt to model the then type
   * of an expression after calling Promise.resolve() on it.
   */
  static final JSType getTemplateTypeOfThenable(JSTypeRegistry registry, JSType maybeThenable) {
    return maybeThenable
        // Without ".restrictByNotNullOrUndefined" we'd get the unknown type for "?IThenable<null>"
        .restrictByNotNullOrUndefined()
        .getInstantiatedTypeArgument(registry.getNativeType(JSTypeNative.I_THENABLE_TYPE));
  }

  /**
   * Returns the type of `await [expr]`.
   *
   * <p>This is equivalent to the type of `result` in `Promise.resolve([expr]).then(result => `
   *
   * <p>For example:
   *
   * <p>{@code !Promise<number>} becomes {@code number}
   *
   * <p>{@code !IThenable<number>} becomes {@code number}
   *
   * <p>{@code string} becomes {@code string}
   *
   * <p>{@code (!Promise<number>|string)} becomes {@code (number|string)}
   *
   * <p>{@code ?Promise<number>} becomes {@code (null|number)}
   */
  static final JSType getResolvedType(JSTypeRegistry registry, JSType type) {
    if (type.isUnknownType()) {
      return type;
    }

    if (type.isUnionType()) {
      UnionTypeBuilder unionTypeBuilder = UnionTypeBuilder.create(registry);
      for (JSType alternate : type.toMaybeUnionType().getAlternatesWithoutStructuralTyping()) {
        unionTypeBuilder.addAlternate(getResolvedType(registry, alternate));
      }
      return unionTypeBuilder.build();
    }

    // If we can find the "IThenable" template key (which is true for Promise and IThenable), return
    // the resolved value. e.g. for "!Promise<string>" return "string".
    TemplateTypeMap templates = type.getTemplateTypeMap();
    if (templates.hasTemplateKey(registry.getIThenableTemplate())) {
      // Call getResolvedPromiseType again in case someone does something unusual like
      // !Promise<!Promise<number>>
      // TODO(lharker): we don't need to handle this case and should report an error for this in a
      // type annotation (not here, maybe in TypeCheck). A Promise cannot resolve to another Promise
      return getResolvedType(
          registry, templates.getResolvedTemplateType(registry.getIThenableTemplate()));
    }

    // Awaiting anything with a ".then" property (other than IThenable, handled above) should return
    // unknown, rather than the type itself.
    if (type.isSubtypeOf(registry.getNativeType(JSTypeNative.THENABLE_TYPE))) {
      return registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    }

    return type;
  }
}
