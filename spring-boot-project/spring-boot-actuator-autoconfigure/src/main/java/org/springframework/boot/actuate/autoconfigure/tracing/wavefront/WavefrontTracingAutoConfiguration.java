/*
 * Copyright 2012-2022 the original author or authors.
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

package org.springframework.boot.actuate.autoconfigure.tracing.wavefront;

import java.util.Collections;

import brave.handler.SpanHandler;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.reporter.wavefront.SpanMetrics;
import io.micrometer.tracing.reporter.wavefront.WavefrontBraveSpanHandler;
import io.micrometer.tracing.reporter.wavefront.WavefrontOtelSpanExporter;
import io.micrometer.tracing.reporter.wavefront.WavefrontSpanHandler;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.ConditionalOnEnabledTracing;
import org.springframework.boot.actuate.autoconfigure.wavefront.WavefrontProperties;
import org.springframework.boot.actuate.autoconfigure.wavefront.WavefrontProperties.Tracing;
import org.springframework.boot.actuate.autoconfigure.wavefront.WavefrontSenderConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Wavefront tracing.
 *
 * @author Moritz Halbritter
 * @author Glenn Oppegard
 * @since 3.0.0
 */
@AutoConfiguration(after = { MetricsAutoConfiguration.class, CompositeMeterRegistryAutoConfiguration.class })
@EnableConfigurationProperties(WavefrontProperties.class)
@ConditionalOnClass(WavefrontSender.class)
@Import(WavefrontSenderConfiguration.class)
@ConditionalOnEnabledTracing
public class WavefrontTracingAutoConfiguration {

	/**
	 * Default value for the Wavefront Application name.
	 * @see <a href=
	 * "https://docs.wavefront.com/trace_data_details.html#application-tags">Wavefront
	 * Application Tags</a>
	 */
	private static final String DEFAULT_WAVEFRONT_APPLICATION_NAME = "unnamed_application";

	/**
	 * Default value for the Wavefront Service name if {@code spring.application.name} is
	 * not set.
	 * @see <a href=
	 * "https://docs.wavefront.com/trace_data_details.html#application-tags">Wavefront
	 * Application Tags</a>
	 */
	private static final String DEFAULT_WAVEFRONT_SERVICE_NAME = "unnamed_service";

	@Bean
	@ConditionalOnMissingBean
	public ApplicationTags applicationTags(Environment environment, WavefrontProperties properties) {
		String fallbackWavefrontServiceName = environment.getProperty("spring.application.name",
				DEFAULT_WAVEFRONT_SERVICE_NAME);
		Tracing tracing = properties.getTracing();
		String wavefrontServiceName = (tracing.getServiceName() != null) ? tracing.getServiceName()
				: fallbackWavefrontServiceName;
		String wavefrontApplicationName = (tracing.getApplicationName() != null) ? tracing.getApplicationName()
				: DEFAULT_WAVEFRONT_APPLICATION_NAME;
		ApplicationTags.Builder builder = new ApplicationTags.Builder(wavefrontApplicationName, wavefrontServiceName);
		if (tracing.getClusterName() != null) {
			builder.cluster(tracing.getClusterName());
		}
		if (tracing.getShardName() != null) {
			builder.shard(tracing.getShardName());
		}
		return builder.build();
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(WavefrontSpanHandler.class)
	static class WavefrontMicrometer {

		@Bean
		@ConditionalOnMissingBean
		@ConditionalOnBean(WavefrontSender.class)
		WavefrontSpanHandler wavefrontSpanHandler(WavefrontProperties properties, WavefrontSender wavefrontSender,
				SpanMetrics spanMetrics, ApplicationTags applicationTags) {
			return new WavefrontSpanHandler(properties.getSender().getMaxQueueSize(), wavefrontSender, spanMetrics,
					properties.getSourceOrDefault(), applicationTags, Collections.emptySet());
		}

		@Configuration(proxyBeanMethods = false)
		@ConditionalOnBean(MeterRegistry.class)
		static class MeterRegistrySpanMetricsConfiguration {

			@Bean
			@ConditionalOnMissingBean
			MeterRegistrySpanMetrics meterRegistrySpanMetrics(MeterRegistry meterRegistry) {
				return new MeterRegistrySpanMetrics(meterRegistry);
			}

		}

		@Configuration(proxyBeanMethods = false)
		@ConditionalOnMissingBean(MeterRegistry.class)
		static class NoopSpanMetricsConfiguration {

			@Bean
			@ConditionalOnMissingBean
			SpanMetrics meterRegistrySpanMetrics() {
				return SpanMetrics.NOOP;
			}

		}

		@Configuration(proxyBeanMethods = false)
		@ConditionalOnClass(SpanHandler.class)
		static class WavefrontBrave {

			@Bean
			@ConditionalOnMissingBean
			WavefrontBraveSpanHandler wavefrontBraveSpanHandler(WavefrontSpanHandler wavefrontSpanHandler) {
				return new WavefrontBraveSpanHandler(wavefrontSpanHandler);
			}

		}

		@Configuration(proxyBeanMethods = false)
		@ConditionalOnClass(SpanExporter.class)
		static class WavefrontOpenTelemetry {

			@Bean
			@ConditionalOnMissingBean
			WavefrontOtelSpanExporter wavefrontOtelSpanExporter(WavefrontSpanHandler wavefrontSpanHandler) {
				return new WavefrontOtelSpanExporter(wavefrontSpanHandler);
			}

		}

	}

}
