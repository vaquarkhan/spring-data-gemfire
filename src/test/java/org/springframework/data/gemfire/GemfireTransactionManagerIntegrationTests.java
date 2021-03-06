/*
 * Copyright 2016 the original author or authors.
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
 *
 */

package org.springframework.data.gemfire;

import static org.assertj.core.api.Assertions.assertThat;

import javax.annotation.Resource;

import com.gemstone.gemfire.cache.GemFireCache;
import com.gemstone.gemfire.cache.Region;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.gemfire.config.annotation.PeerCacheApplication;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * Integration tests for the {@link GemfireTransactionManager}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.springframework.data.gemfire.GemfireTransactionManager
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @see org.springframework.transaction.annotation.EnableTransactionManagement
 * @see org.springframework.transaction.annotation.Propagation
 * @since 1.9.1
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes =
	GemfireTransactionManagerIntegrationTests.GemfireTransactionManagerIntegrationTestsConfiguration.class)
public class GemfireTransactionManagerIntegrationTests {

	protected static final String GEMFIRE_LOG_LEVEL = "warning";

	@Resource(name = "Example")
	@SuppressWarnings("unused")
	private Region<Object, Object> example;

	@Autowired
	@SuppressWarnings("all")
	private SuspendAndResumeCacheTransactionsService service;

	@Test(expected = IllegalArgumentException.class)
	public void suspendAndResumeIsSuccessful() {
		try {
			assertThat(example).isEmpty();

			service.doCacheTransactions();
		}
		catch (IllegalArgumentException e) {
			assertThat(e).hasMessage("boom!");
			assertThat(e).hasNoCause();

			throw e;
		}
		finally {
			assertThat(example).hasSize(1);
			assertThat(example.containsKey("tx-1-op-1")).isFalse();
			assertThat(example.containsKey("tx-2-op-1")).isTrue();
		}
	}

	@Configuration
	@EnableTransactionManagement
	@Import(GemFireConfiguration.class)
	@SuppressWarnings("unused")
	static class GemfireTransactionManagerIntegrationTestsConfiguration {

		@Bean
		GemfireTransactionManager transactionManager(GemFireCache gemfireCache) {
			return new GemfireTransactionManager(gemfireCache);
		}

		@Bean
		SuspendAndResumeCacheTransactionsRepository suspendAndResumeCacheTransactionsRepository(GemFireCache gemFireCache) {
			return new SuspendAndResumeCacheTransactionsRepository(gemFireCache.getRegion("Example"));
		}

		@Bean
		SuspendAndResumeCacheTransactionsService suspendAndResumeCacheTransactionsService(
				SuspendAndResumeCacheTransactionsRepository repository) {

			return new SuspendAndResumeCacheTransactionsService(repository);
		}
	}

	@SuppressWarnings("unused")
	@PeerCacheApplication(name = "GemfireTransactionManagerIntegrationTests", logLevel = GEMFIRE_LOG_LEVEL)
	static class GemFireConfiguration {

		@Bean(name = "Example")
		LocalRegionFactoryBean<Object, Object> exampleRegion(GemFireCache gemfireCache) {
			LocalRegionFactoryBean<Object, Object> example = new LocalRegionFactoryBean<Object, Object>();

			example.setCache(gemfireCache);
			example.setClose(false);
			example.setPersistent(false);

			return example;
		}
	}

	@Service
	static class SuspendAndResumeCacheTransactionsService {

		SuspendAndResumeCacheTransactionsRepository repository;

		SuspendAndResumeCacheTransactionsService(SuspendAndResumeCacheTransactionsRepository repository) {
			Assert.notNull(repository, "Repository must not be null");
			this.repository = repository;
		}

		@Transactional
		public void doCacheTransactions() {
			repository.doOperationOneInTransactionOne();
			repository.doOperationOneInTransactionTwo();
			repository.doOperationTwoInTransactionOne();
		}
	}

	@Repository
	static class SuspendAndResumeCacheTransactionsRepository {

		@SuppressWarnings("all")
		private Region<Object, Object> example;

		SuspendAndResumeCacheTransactionsRepository(Region<Object, Object> example) {
			Assert.notNull(example, "Region must not be null");
			this.example = example;
		}

		@Transactional(propagation = Propagation.REQUIRED)
		public void doOperationOneInTransactionOne() {
			example.put("tx-1-op-1", "test");
		}

		@Transactional(propagation = Propagation.REQUIRES_NEW)
		public void doOperationOneInTransactionTwo() {
			example.put("tx-2-op-1", "test");
		}

		@Transactional(propagation = Propagation.REQUIRED)
		public void doOperationTwoInTransactionOne() {
			throw new IllegalArgumentException("boom!");
		}
	}
}
