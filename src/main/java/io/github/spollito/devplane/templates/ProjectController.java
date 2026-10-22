package io.github.spollito.devplane.templates;

import io.github.spollito.devplane.catalog.RegisteredService;
import io.github.spollito.devplane.catalog.ServiceCatalog;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Downloads the project generated for a registered service. Nothing is stored: each
 * request generates the project from the service's current catalog details.
 */
@RestController
class ProjectController {

	static final String APPLICATION_ZIP_VALUE = "application/zip";

	private static final MediaType APPLICATION_ZIP = MediaType.parseMediaType(APPLICATION_ZIP_VALUE);

	private final ServiceTemplates templates;

	private final ServiceCatalog catalog;

	private final ProjectArchiver archiver;

	ProjectController(ServiceTemplates templates, ServiceCatalog catalog, ProjectArchiver archiver) {
		this.templates = templates;
		this.catalog = catalog;
		this.archiver = archiver;
	}

	/**
	 * Returns the service's project as {@code <name>.zip}, holding a {@code <name>/}
	 * folder. A {@link Resource} body is documented as a binary file.
	 */
	@GetMapping(path = "/api/v1/services/{name}/project", produces = APPLICATION_ZIP_VALUE)
	ResponseEntity<Resource> download(@PathVariable String name,
			@RequestParam(defaultValue = ServiceTemplates.DEFAULT) String template) {
		// Checked before the service because it needs no database.
		if (!templates.isListed(template)) {
			throw new UnknownTemplateException(template, templates.all());
		}
		RegisteredService service = catalog.lookUp(name);
		byte[] archive = archiver.archive(template,
				new NewProject(service.name(), service.owner(), service.description()));
		return ResponseEntity.ok()
			.contentType(APPLICATION_ZIP)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(service.name() + ".zip").build().toString())
			.body(new ByteArrayResource(archive));
	}

}
