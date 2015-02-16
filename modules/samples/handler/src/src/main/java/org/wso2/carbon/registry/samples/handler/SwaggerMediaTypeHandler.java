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
	private String swaggerDefinitionUrl;

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

		OMElement metadata;
		try {
			StAXOMBuilder builder = new StAXOMBuilder(in);
			metadata = builder.getDocumentElement();
		} catch (Exception e) {
			throw new RegistryException("Failed to parse the rest api.");
		}

		OMElement overview = metadata.getFirstChildWithName(new QName(CommonConstants.METADATA_NAMESPACE_URL,"overview"));

		if(overview == null) {
			throw new RegistryException("Resource metadata cannot be null.");
		}

		OMElement urlElement = overview.getFirstChildWithName(new QName(CommonConstants.METADATA_NAMESPACE_URL,"swaggerDefinitionURL"));
		this.swaggerDefinitionUrl = urlElement.getText();

		resource.setProperty("Swagger Definition URL", this.swaggerDefinitionUrl);
		resource.setProperty("Created at",
		                     requestContext.getResource().getCreatedTime().toString());
		requestContext.getRepository().put(resourcePath, resource);
		requestContext.setProcessingComplete(true);
	}
}
