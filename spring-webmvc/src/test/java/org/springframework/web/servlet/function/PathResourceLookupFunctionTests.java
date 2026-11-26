/*
 * Copyright 2002-2023 the original author or authors.
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

package org.springframework.web.servlet.function;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.handler.PathPatternsTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Arjen Poutsma
 */
class PathResourceLookupFunctionTests {

	@TempDir
	Path tempDir;

	@Test
	void normal() throws Exception {
		ClassPathResource location = new ClassPathResource("org/springframework/web/servlet/function/");
		PathResourceLookupFunction function = new PathResourceLookupFunction("/resources/**", location);
		ServerRequest request = initRequest("GET", "/resources/response.txt");

		Optional<Resource> result = function.apply(request);
		assertThat(result).isPresent();

		File expected = new ClassPathResource("response.txt", getClass()).getFile();
		assertThat(result.get().getFile()).isEqualTo(expected);
	}

	@Test
	void subPath() throws Exception {
		ClassPathResource location = new ClassPathResource("org/springframework/web/servlet/function/");
		PathResourceLookupFunction function = new PathResourceLookupFunction("/resources/**", location);
		ServerRequest request = initRequest("GET", "/resources/child/response.txt");

		Optional<Resource> result = function.apply(request);
		assertThat(result).isPresent();

		File expected = new ClassPathResource("org/springframework/web/servlet/function/child/response.txt").getFile();
		assertThat(result.get().getFile()).isEqualTo(expected);
	}

	@Test
	void notFound() {
		ClassPathResource location = new ClassPathResource("org/springframework/web/reactive/function/server/");
		PathResourceLookupFunction function = new PathResourceLookupFunction("/resources/**", location);
		ServerRequest request = initRequest("GET", "/resources/foo.txt");

		Optional<Resource> result = function.apply(request);
		assertThat(result).isNotPresent();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"link/%2e%2e/secret.txt",
			"link/%2E%2E/secret.txt",
			"link%2f%2e%2e%2fsecret.txt",
			"link/..%2fsecret.txt",
			"link/%252e%252e/secret.txt"
	})
	@DisabledOnOs(value = OS.WINDOWS, disabledReason = "Creating symbolic links requires elevated privileges")
	void fileSystemResourceLocationWithEncodedTraversalThroughSymlink(String path) throws Exception {
		// An encoded "../" that StringUtils#cleanPath removes passes validation, but
		// the file system resolves ".." against the symlink target, outside the location.
		Resource location = initFileSystemLocation();
		Files.createDirectories(this.tempDir.resolve("outside"));
		Files.createSymbolicLink(this.tempDir.resolve("static/link"), this.tempDir.resolve("outside"));
		PathResourceLookupFunction function = new PathResourceLookupFunction("/resources/**", location);

		Optional<Resource> result = function.apply(initRequest("GET", "/resources/" + path));
		assertThat(result).isNotPresent();
	}

	@Test
	void composeResourceLookupFunction() throws Exception {
		ClassPathResource defaultResource = new ClassPathResource("response.txt", getClass());

		Function<ServerRequest, Optional<Resource>> lookupFunction =
				new PathResourceLookupFunction("/resources/**",
						new ClassPathResource("org/springframework/web/servlet/function/"));

		Function<ServerRequest, Optional<Resource>> customLookupFunction =
				lookupFunction.andThen((Optional<Resource> optionalResource) -> {
					if (optionalResource.isPresent()) {
						return optionalResource;
					}
					else {
						return Optional.of(defaultResource);
					}
				});

		ServerRequest request = initRequest("GET", "/resources/foo");

		Optional<Resource> result = customLookupFunction.apply(request);
		assertThat(result).isPresent();

		assertThat(result.get().getFile()).isEqualTo(defaultResource.getFile());
	}

	private Resource initFileSystemLocation() throws Exception {
		Path location = Files.createDirectories(this.tempDir.resolve("static"));
		Files.writeString(this.tempDir.resolve("secret.txt"), "secret");
		return new FileSystemResource(location.toString() + "/");
	}

	private ServerRequest initRequest(String httpMethod, String requestUri) {
		return new DefaultServerRequest(
				PathPatternsTestUtils.initRequest(httpMethod, requestUri, true),
				Collections.emptyList());
	}

}
