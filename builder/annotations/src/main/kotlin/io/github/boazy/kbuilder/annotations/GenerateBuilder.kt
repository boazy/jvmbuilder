/*
 * Copyright (C) 2018 Jose Francisco Fiorillo Verenzuela
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.boazy.kbuilder.annotations

import kotlin.annotation.AnnotationTarget.CLASS

@Target(CLASS)
annotation class GenerateBuilder(
    /**
     * Specify a custom name to use for the builder class.
     *
     * If this parameter is left unspecified, the builder class suffix
     * (`Builder` by default) is added to the target class name.
     */
    val className: String = "",

    /**
     * Prefix for builder methods, e.g. `with` or `set`.
     *
     * This parameter is empty (no prefix) by default.
     */
    val prefix: String = "",

    /**
     * Indicates whether class copies should be optimized.
     *
     * If the data class has some default values which need be calculated or
     * recreated on each new instantiation, or if it has some mutable fields
     * which are not safe to share between copies, we need to be careful with
     * some copy-based optimizations, so we do not perform them by default and
     * instead require this flag to be explicitly enabled.
     */
    val optimizeCopy: Boolean = false
)
