package com.github.standalone_openig.config;

import java.io.File;
import java.net.URI;

import javax.servlet.ServletContext;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.config.ConfigUtil;
import org.forgerock.openig.json.JSONRepresentation;
import org.forgerock.openig.resource.FileResource;
import org.forgerock.openig.resource.Representation;
import org.forgerock.openig.resource.Resource;
import org.forgerock.openig.resource.ResourceException;
import org.forgerock.openig.resource.Resources;
 
/**
 * Locate config.json in /WEB-INF/config.json.
 */
public class WebinfConfigResource implements Resource {
 
   /** The underlying resource that this object represents. */
   private final Resource resource;
 
   /**
     * Constructs a new configuration resource, with a path based-on the specified vendor,
     * product and servlet context.
     *
     * @param vendor the vendor name.
     * @param product the product name.
     * @param context the servlet context from which the product instance name can be derived.
     * @throws ResourceException if the configuration (or bootstrap) resource could not be found.
     */
   public WebinfConfigResource(String vendor, String product, ServletContext context) throws ResourceException {
       this(vendor, product, context.getRealPath("/"));
   }
 
   /**
     * Constructs a new configuration resource, with a path based-on the specified vendor,
     * product and instance name.
     *
     * @param vendor the vendor name.
     * @param product the product name.
     * @param instance the product instance name.
     * @throws ResourceException if the configuration (or bootstrap) resource could not be found.
     */
   public WebinfConfigResource(String vendor, String product, String instance) throws ResourceException {

       File config = null;
	   if(instance != null){
		   config = new File(instance, "/WEB-INF/config.json");
	   }
	   
       if (config != null && config.exists()) { // simplistic config.json file
           this.resource = new FileResource(config);
       }
       else if(config == null){
	       config = ConfigUtil.getFile(vendor, product, "config");
           this.resource = new FileResource(config);
       } else { // bootstrap location of instance-based configuration file
           File boot = ConfigUtil.getFile(vendor, product, instance != null ? instance : "bootstrap");
           if (!boot.exists()) {
               throw new ResourceException("could not find local configuration file at " +
                config.getPath() + " or bootstrap file at " + boot.getPath());
           }
           FileResource bootResource = new FileResource(boot);
           JSONRepresentation representation = new JSONRepresentation();
           bootResource.read(representation);
           try {
               this.resource = Resources.newInstance(new JsonValue(representation.object).get("configURI").required().asURI());
           } catch (JsonValueException jve) {
               throw new ResourceException(jve);
           }
       }
   }
 
   public void create(Representation representation) throws ResourceException {
       resource.create(representation);
   }
 
   public void read(Representation representation) throws ResourceException {
       resource.read(representation);
   }
 
   public void update(Representation representation) throws ResourceException {
       resource.update(representation);
   }
 
   public void delete() throws ResourceException {
       resource.delete();
   }
 
   public URI getURI() throws ResourceException {
       return resource.getURI();
   }
}