package org.wso2.carbon.registry.samples.handler;

import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.jdbc.handlers.RequestContext;

public class SwaggerMediaTypeHandler {

    public void put(RequestContext requestContext) throws RegistryException {

        Resource resource =requestContext.getResource();
        Object o = resource.getContent();
        String resourcePath = requestContext.getResourcePath().getPath();
        Registry registry = requestContext.getRegistry();

        resource.setProperty("Resource Path", resourcePath);
        resource.setProperty("Created at", requestContext.getResource().getCreatedTime().toString());
        requestContext.getRepository().put(resourcePath,resource);
        requestContext.setProcessingComplete(true);
    }
}
