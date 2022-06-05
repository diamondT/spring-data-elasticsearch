/*
 * Copyright 2021-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.elasticsearch.repository.support;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.core.convert.ConversionService;
import org.springframework.data.elasticsearch.repository.query.ElasticsearchParameterAccessor;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.util.NumberUtils;

/**
 * @author Peter-Josef Meisch
 * @author Niklas Herder
 */
final public class StringQueryUtil {

	private static final Pattern PARAMETER_PLACEHOLDER = Pattern.compile("\\?(\\d+)");

	private final ConversionService conversionService;

	private final boolean useNamedParameters;

	public StringQueryUtil(ConversionService conversionService, boolean useNamedParameters) {
		this.conversionService = conversionService;
		this.useNamedParameters = useNamedParameters;
	}

	public String replacePlaceholders(String input, ElasticsearchParameterAccessor accessor) {
		if (useNamedParameters) {
			return replaceNamedParameters(input, accessor);
		}

		Matcher matcher = PARAMETER_PLACEHOLDER.matcher(input);
		String result = input;
		while (matcher.find()) {

			String placeholder = Pattern.quote(matcher.group()) + "(?!\\d+)";
			int index = NumberUtils.parseNumber(matcher.group(1), Integer.class);
			result = result.replaceAll(placeholder, Matcher.quoteReplacement(getParameterWithIndex(accessor, index)));
		}
		return result;
	}

	private String replaceNamedParameters(String input, ElasticsearchParameterAccessor accessor) {

		final String[] result = { input };
		accessor.getParameters().forEach(parameter -> result[0] = result[0]
				.replaceAll(Pattern.quote(parameter.getPlaceholder()), getParameterWithIndex(accessor, parameter.getIndex())));
		return result[0];
	}

	private String getParameterWithIndex(ParameterAccessor accessor, int index) {

		Object parameter = accessor.getBindableValue(index);
		String parameterValue = "null";

		// noinspection ConstantConditions
		if (parameter != null) {

			parameterValue = convert(parameter);
		}

		return parameterValue;

	}

	private String convert(Object parameter) {
		if (Collection.class.isAssignableFrom(parameter.getClass())) {
			Collection<?> collectionParam = (Collection<?>) parameter;
			StringBuilder sb = new StringBuilder("[");
			sb.append(collectionParam.stream().map(o -> {
				if (o instanceof String) {
					return "\"" + convert(o) + "\"";
				} else {
					return convert(o);
				}
			}).collect(Collectors.joining(",")));
			sb.append("]");
			return sb.toString();
		} else {
			String parameterValue = "null";
			if (conversionService.canConvert(parameter.getClass(), String.class)) {
				String converted = conversionService.convert(parameter, String.class);

				if (converted != null) {
					parameterValue = converted;
				}
			} else {
				parameterValue = parameter.toString();
			}

			parameterValue = parameterValue.replaceAll("\"", Matcher.quoteReplacement("\\\""));
			return parameterValue;
		}
	}

}
