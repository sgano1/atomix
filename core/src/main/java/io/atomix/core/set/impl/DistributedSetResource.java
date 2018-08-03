/*
 * Copyright 2017-present Open Networking Foundation
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
package io.atomix.core.set.impl;

import io.atomix.core.collection.impl.DistributedCollectionResource;
import io.atomix.core.set.AsyncDistributedSet;
import io.atomix.core.set.DistributedSetConfig;
import io.atomix.core.set.DistributedSetType;
import io.atomix.primitive.PrimitiveType;

import javax.ws.rs.Path;

/**
 * Distributed set resource.
 */
@Path("/set")
public class DistributedSetResource extends DistributedCollectionResource<AsyncDistributedSet<String>, DistributedSetConfig> {
  public DistributedSetResource() {
    super(DistributedSetType.instance());
  }
}
