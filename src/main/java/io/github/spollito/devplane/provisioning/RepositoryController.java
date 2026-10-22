package io.github.spollito.devplane.provisioning;

import io.github.spollito.devplane.catalog.SourceRepository;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RepositoryController {

	private final RepositoryProvisioner provisioner;

	RepositoryController(RepositoryProvisioner provisioner) {
		this.provisioner = provisioner;
	}

	/**
	 * Creates the service's GitHub repository and pushes its generated project. The
	 * repository on GitHub is what this request creates, so {@code Location} is its
	 * GitHub URL. {@code @ResponseStatus} documents the 201 in the OpenAPI description.
	 */
	@PostMapping("/api/v1/services/{name}/repository")
	@ResponseStatus(HttpStatus.CREATED)
	ResponseEntity<SourceRepository> provision(@PathVariable String name,
			@Valid @RequestBody(required = false) @Nullable ProvisionRepositoryRequest request) {
		ProvisionRepositoryRequest options = (request != null) ? request : ProvisionRepositoryRequest.DEFAULTS;
		SourceRepository repository = provisioner.provision(name, options.templateOrDefault(), options.isPrivate());
		return ResponseEntity.created(repository.url()).body(repository);
	}

}
