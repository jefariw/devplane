package io.github.spollito.devplane.catalog;

import java.net.URI;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/services")
class ServiceCatalogController {

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

}
