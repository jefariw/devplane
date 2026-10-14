package io.github.spollito.devplane.catalog;

import java.net.URI;

import io.github.spollito.devplane.api.PageResponse;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/services")
class ServiceCatalogController {

	static final int DEFAULT_PAGE_SIZE = 20;

	static final int MAX_PAGE_SIZE = 100;

	private final ServiceCatalog catalog;

	ServiceCatalogController(ServiceCatalog catalog) {
		this.catalog = catalog;
	}

	@PostMapping
	ResponseEntity<ServiceResponse> register(@Valid @RequestBody RegisterServiceRequest request) {
		Lifecycle lifecycle = (request.lifecycle() != null) ? Lifecycle.fromApiValue(request.lifecycle()) : null;
		ServiceEntity service = catalog.register(request.name(), request.owner(), request.description(), lifecycle);
		URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
			.path("/{name}")
			.buildAndExpand(service.getName())
			.toUri();
		return ResponseEntity.created(location).body(ServiceResponse.from(service));
	}

	@GetMapping("/{name}")
	ServiceResponse get(@PathVariable String name) {
		return ServiceResponse.from(catalog.get(name));
	}

	/**
	 * Lists services sorted by name. Pages are numbered from 0; out-of-range values
	 * are clamped rather than rejected, and the page size is capped at 100.
	 */
	@GetMapping
	PageResponse<ServiceResponse> list(@RequestParam(required = false) @Nullable String owner,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
		PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
				Sort.by("name"));
		return PageResponse.from(catalog.list(owner, pageRequest).map(ServiceResponse::from));
	}

}
