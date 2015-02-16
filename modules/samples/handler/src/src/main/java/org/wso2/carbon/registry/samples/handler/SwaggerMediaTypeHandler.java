package org.wso2.carbon.registry.samples.handler;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.jdbc.handlers.Handler;
import org.wso2.carbon.registry.core.jdbc.handlers.RequestContext;
import org.wso2.carbon.registry.samples.handler.util.CommonConstants;

import javax.xml.namespace.QName;
import java.io.ByteArrayInputStream;

@SuppressWarnings("unused")
public class SwaggerMediaTypeHandler extends Handler {

	private String swaggerVersion = CommonConstants.DEFAULT_SWAGGER_VERSION;
	private String swaggerMediaType = "application/swagger+json";

	public void put(RequestContext requestContext) throws RegistryException {

		Resource resource = requestContext.getResource();
		Object o = resource.getContent();
		String resourcePath = requestContext.getResourcePath().getPath();
		Registry registry = requestContext.getRegistry();

		if (o == null || !(o instanceof byte[])) {
			String msg = "Content cannot be null";
			throw new RegistryException(msg);
		}

		byte[] content = (byte[]) resource.getContent();
		ByteArrayInputStream in = new ByteArrayInputStream(content);

		OMElement apiDefinition;
		try {
			StAXOMBuilder builder = new StAXOMBuilder(in);
			apiDefinition = builder.getDocumentElement();
		} catch (Exception ae) {
			throw new RegistryException("Failed to parse the rest api.");
		}

		OMElement titleElement = apiDefinition.getFirstChildWithName(new QName("title"));

		if (titleElement == null) {
			throw new RegistryException("API title cannot be NULL.");
		}
		OMElement descElement = apiDefinition.getFirstChildWithName(new QName("description"));

		if (descElement != null) {
			resource.setDescription(descElement.getText());
		}
		OMElement uriElement =
				apiDefinition.getFirstChildWithName(new QName("swaggerDefinitionURL"));

		if (uriElement == null) {
			throw new RegistryException("API definition URL cannot be null.");
		}

		resource.setProperty("Resource Path", resourcePath);
		resource.setProperty("Created at",
		                     requestContext.getResource().getCreatedTime().toString());
		requestContext.getRepository().put(resourcePath, resource);
		registry.applyTag(resourcePath, "complete");
		requestContext.setProcessingComplete(true);
	}
}
