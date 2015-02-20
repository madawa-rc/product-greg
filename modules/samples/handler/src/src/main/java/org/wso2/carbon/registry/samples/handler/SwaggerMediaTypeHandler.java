package org.wso2.carbon.registry.samples.handler;

import org.wso2.carbon.registry.common.utils.artifact.manager.ArtifactManager;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.ResourceImpl;
import org.wso2.carbon.registry.core.config.RegistryContext;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.jdbc.handlers.Handler;
import org.wso2.carbon.registry.core.jdbc.handlers.RequestContext;
import org.wso2.carbon.registry.core.utils.RegistryUtils;
import org.wso2.carbon.registry.extensions.utils.CommonUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

public class SwaggerMediaTypeHandler extends Handler {

	public static final String SWAGGER_VERSION_DEFAULT_VALUE = "1.0.0";

	private String location = "/swaggers/";

	@Override public void put(RequestContext requestContext) throws RegistryException {
		if (!CommonUtil.isUpdateLockAvailable()) {
			return;
		}
		CommonUtil.acquireUpdateLock();

		try {
			if (requestContext == null) {
				throw new RegistryException("The request context is not available.");
			}
			String path = requestContext.getResourcePath().getPath();
			Resource resource = requestContext.getResource();
			Registry registry = requestContext.getRegistry();

			Object resourceContentObj = resource.getContent();
			String resourceContent; // here the resource content is url
			if (resourceContentObj instanceof String) {
				resourceContent = (String)resourceContentObj;
				resource.setContent(RegistryUtils.encodeString(resourceContent));
			} else {
				resourceContent = RegistryUtils.decodeBytes((byte[])resourceContentObj);
			}
			try {
				if (registry.resourceExists(path)) {
					Resource oldResource = registry.get(path);
					byte[] oldContent = (byte[])oldResource.getContent();
					if (oldContent != null && RegistryUtils.decodeBytes(oldContent).equals(resourceContent)) {
						// this will continue adding from the default path.
						return;
					}
				}
			} catch (Exception e) {
				String msg = "Error in comparing the policy content updates. policy path: " + path + ".";
				//log.error(msg, e);
				throw new RegistryException(msg, e);
			}
			Object newContent = RegistryUtils.encodeString((String)resourceContent);
			if (newContent != null) {
				InputStream inputStream = new ByteArrayInputStream((byte[])newContent);
				addSwaggerToRegistry(requestContext, inputStream);
			}
			ArtifactManager.getArtifactManager().getTenantArtifactRepository().addArtifact(path);
		} finally {
			CommonUtil.releaseUpdateLock();
		}
	}

	@Override public void importResource(RequestContext requestContext) throws RegistryException {

		if (!CommonUtil.isUpdateLockAvailable()) {
			return;
		}
		CommonUtil.acquireUpdateLock();

		try {
			String sourceURL = requestContext.getSourceURL();
			InputStream inputStream;
			try {
				if (sourceURL != null && sourceURL.toLowerCase().startsWith("file:")) {
					String msg =
							"The source URL must not be file in the server's local file system";
					throw new RegistryException(msg);
				}
				inputStream = new URL(sourceURL).openStream();
			} catch (IOException e) {
				throw new RegistryException("The URL " + sourceURL + " is incorrect.", e);
			}
			addSwaggerToRegistry(requestContext, inputStream);

		} finally {
			CommonUtil.releaseUpdateLock();
		}

	}

	private void addSwaggerToRegistry(RequestContext requestContext, InputStream inputStream)
			throws RegistryException {
		Resource swaggerResource;
		if (requestContext.getResource() == null) {
			swaggerResource = new ResourceImpl();
			//swaggerResource.setMediaType("application/swagger+json");
		} else {
			swaggerResource = requestContext.getResource();
		}

		String version =
				requestContext.getResource().getProperty(RegistryConstants.VERSION_PARAMETER_NAME);
		if (version == null) {
			version = SWAGGER_VERSION_DEFAULT_VALUE;
			requestContext.getResource()
			              .setProperty(RegistryConstants.VERSION_PARAMETER_NAME, version);
		}

		Registry registry = requestContext.getRegistry();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		int nextChar;
		try {
			while ((nextChar = inputStream.read()) != -1) {
				outputStream.write(nextChar);
			}
			outputStream.flush();
		} catch (IOException e) {
			throw new RegistryException("Exception occurred while reading swagger content", e);
		}

		String resourcePath = requestContext.getResourcePath().getPath();
		String swaggerFileName = resourcePath
				.substring(resourcePath.lastIndexOf(RegistryConstants.PATH_SEPARATOR) + 1);
		Registry systemRegistry = CommonUtil.getUnchrootedSystemRegistry(requestContext);
		RegistryContext registryContext = requestContext.getRegistryContext();
		String commonLocation = getChrootedLocation(registryContext);
		if (!systemRegistry.resourceExists(commonLocation)) {
			systemRegistry.put(commonLocation, systemRegistry.newCollection());
		}

		String swaggerPath;
		if (!resourcePath.startsWith(commonLocation)
		        && !resourcePath.equals(RegistryUtils.getAbsolutePath(registryContext,
		                                                              RegistryConstants.PATH_SEPARATOR +
		                                                              swaggerFileName))
		        && !resourcePath.equals(RegistryUtils.getAbsolutePath(registryContext,
		                                               RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH +
		                                               RegistryConstants.PATH_SEPARATOR + swaggerFileName))) {
			swaggerPath = resourcePath;
		} else {
			swaggerPath = commonLocation + version + "/" + swaggerFileName;
		}

		String relativeArtifactPath = RegistryUtils.getRelativePath(registry.getRegistryContext(), swaggerPath);

		relativeArtifactPath = RegistryUtils.getRelativePathToOriginal(relativeArtifactPath,
		                                                               RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH);

		Resource newResource;
		if (registry.resourceExists(swaggerPath)) {
			newResource = registry.get(swaggerPath);
		} else {
			newResource = new ResourceImpl();
			Properties properties = swaggerResource.getProperties();
			if (properties != null) {
				List<String> linkProperties = Arrays
						.asList(RegistryConstants.REGISTRY_LINK, RegistryConstants.REGISTRY_USER,
						        RegistryConstants.REGISTRY_MOUNT, RegistryConstants.REGISTRY_AUTHOR,
						        RegistryConstants.REGISTRY_MOUNT_POINT,
						        RegistryConstants.REGISTRY_TARGET_POINT,
						        RegistryConstants.REGISTRY_ACTUAL_PATH,
						        RegistryConstants.REGISTRY_REAL_PATH);
				for (Map.Entry<Object, Object> e : properties.entrySet()) {
					String key = (String) e.getKey();
					if (!linkProperties.contains(key)) {
						newResource.setProperty(key, (List<String>) e.getValue());
					}
				}
			}
		}

		newResource.setMediaType("text/plain");
		String policyId = swaggerResource.getUUID();
		if (policyId == null) {
			// generate a service id
			policyId = UUID.randomUUID().toString();
		}
		newResource.setUUID(policyId);
		newResource.setContent(new String(outputStream.toByteArray()));
		addSwaggerToRegistry(requestContext, swaggerPath, requestContext.getSourceURL(),
		                     newResource, registry);
		((ResourceImpl)newResource).setPath(relativeArtifactPath);

		requestContext.setResource(newResource);
		requestContext.setProcessingComplete(true);

	}

	private String getChrootedLocation(RegistryContext registryContext) {
		return RegistryUtils.getAbsolutePath(registryContext,
		                                     RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH +
		                                     location);
	}

	protected void addSwaggerToRegistry(RequestContext context, String path, String url,
	                                   Resource resource, Registry registry) throws RegistryException {
		context.setActualPath(path);
		registry.put(path, resource);
	}
}
