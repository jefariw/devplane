package io.github.spollito.devplane.templates;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/templates")
class TemplateController {

	private final ServiceTemplates templates;

	TemplateController(ServiceTemplates templates) {
		this.templates = templates;
	}

	@GetMapping
	List<ServiceTemplate> list() {
		return templates.all();
	}

}
