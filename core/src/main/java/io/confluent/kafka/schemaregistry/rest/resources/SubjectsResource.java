/**
 * Copyright 2014 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.confluent.kafka.schemaregistry.rest.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;

import io.confluent.kafka.schemaregistry.client.rest.Versions;
import io.confluent.kafka.schemaregistry.client.rest.entities.requests.RegisterSchemaRequest;
import io.confluent.kafka.schemaregistry.rest.entities.Schema;
import io.confluent.kafka.schemaregistry.rest.exceptions.Errors;
import io.confluent.kafka.schemaregistry.storage.KafkaSchemaRegistry;
import io.confluent.kafka.schemaregistry.storage.exceptions.SchemaRegistryException;
import io.confluent.rest.annotations.PerformanceMetric;

@Path("/subjects")
@Produces({Versions.SCHEMA_REGISTRY_V1_JSON_WEIGHTED,
           Versions.SCHEMA_REGISTRY_DEFAULT_JSON_WEIGHTED,
           Versions.JSON_WEIGHTED})
@Consumes({Versions.SCHEMA_REGISTRY_V1_JSON,
           Versions.SCHEMA_REGISTRY_DEFAULT_JSON,
           Versions.JSON, Versions.GENERIC_REQUEST})
public class SubjectsResource {

  public final static String MESSAGE_SUBJECT_NOT_FOUND = "Subject not found.";
  private static final Logger log = LoggerFactory.getLogger(SubjectsResource.class);
  private final KafkaSchemaRegistry schemaRegistry;

  public SubjectsResource(KafkaSchemaRegistry schemaRegistry) {
    this.schemaRegistry = schemaRegistry;
  }

  @POST
  @Path("/{subject}")
  @PerformanceMetric("subjects.get-schema")
  public void lookUpSchemaUnderSubject(final @Suspended AsyncResponse asyncResponse,
                                       final @HeaderParam("Content-Type") String contentType,
                                       final @HeaderParam("Accept") String accept,
                                       @PathParam("subject") String subject,
                                       RegisterSchemaRequest request) {
    // returns version if the schema exists. Otherwise returns 404
    Map<String, String> headerProperties = new HashMap<String, String>();
    headerProperties.put("Content-Type", contentType);
    headerProperties.put("Accept", accept);
    Schema schema = new Schema(subject, 0, 0, request.getSchema());
    io.confluent.kafka.schemaregistry.client.rest.entities.Schema matchingSchema = null;
    try {
      if (!schemaRegistry.listSubjects().contains(subject)) {
        throw Errors.subjectNotFoundException();
      }
      matchingSchema = schemaRegistry.lookUpSchemaUnderSubjectOrForward(subject, schema, 
                                                                        headerProperties);
    } catch (SchemaRegistryException e) {
      throw new ClientErrorException(Response.Status.INTERNAL_SERVER_ERROR, e);
    }
    if (matchingSchema == null) {
      throw Errors.schemaNotFoundException();
    }
    asyncResponse.resume(matchingSchema);
  }

  @Path("/{subject}/versions")
  public SubjectVersionsResource getSchemas(@PathParam("subject") String subject) {
    if (subject != null) {
      subject = subject.trim();
    } else {
      throw new NotFoundException(MESSAGE_SUBJECT_NOT_FOUND);
    }
    return new SubjectVersionsResource(schemaRegistry, subject);
  }

  @GET
  @Valid
  @PerformanceMetric("subjects.list")
  public Set<String> list() {
    try {
      return schemaRegistry.listSubjects();
    } catch (SchemaRegistryException e) {
      throw new ClientErrorException(Response.Status.INTERNAL_SERVER_ERROR, e);
    }
  }
}
