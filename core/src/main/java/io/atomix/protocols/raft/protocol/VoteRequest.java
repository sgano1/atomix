/*
 * Copyright 2015-present Open Networking Laboratory
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
package io.atomix.protocols.raft.protocol;

import io.atomix.cluster.NodeId;

import java.util.Objects;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Server vote request.
 * <p>
 * Vote requests are sent by candidate servers during an election to determine whether they should
 * become the leader for a cluster. Vote requests contain the necessary information for followers to
 * determine whether a candidate should receive their vote based on log and other information.
 */
public class VoteRequest extends AbstractRaftRequest {

  /**
   * Returns a new vote request builder.
   *
   * @return A new vote request builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  private final long term;
  private final NodeId candidate;
  private final long logIndex;
  private final long logTerm;

  public VoteRequest(long term, NodeId candidate, long logIndex, long logTerm) {
    this.term = term;
    this.candidate = candidate;
    this.logIndex = logIndex;
    this.logTerm = logTerm;
  }

  /**
   * Returns the requesting node's current term.
   *
   * @return The requesting node's current term.
   */
  public long term() {
    return term;
  }

  /**
   * Returns the candidate's address.
   *
   * @return The candidate's address.
   */
  public NodeId candidate() {
    return candidate;
  }

  /**
   * Returns the candidate's last log index.
   *
   * @return The candidate's last log index.
   */
  public long logIndex() {
    return logIndex;
  }

  /**
   * Returns the candidate's last log term.
   *
   * @return The candidate's last log term.
   */
  public long logTerm() {
    return logTerm;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), term, candidate, logIndex, logTerm);
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof VoteRequest) {
      VoteRequest request = (VoteRequest) object;
      return request.term == term
          && request.candidate == candidate
          && request.logIndex == logIndex
          && request.logTerm == logTerm;
    }
    return false;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("term", term)
        .add("candidate", candidate)
        .add("logIndex", logIndex)
        .add("logTerm", logTerm)
        .toString();
  }

  /**
   * Vote request builder.
   */
  public static class Builder extends AbstractRaftRequest.Builder<Builder, VoteRequest> {
    private long term = -1;
    private NodeId candidate;
    private long logIndex = -1;
    private long logTerm = -1;

    /**
     * Sets the request term.
     *
     * @param term The request term.
     * @return The poll request builder.
     * @throws IllegalArgumentException if {@code term} is negative
     */
    public Builder withTerm(long term) {
      checkArgument(term >= 0, "term must be positive");
      this.term = term;
      return this;
    }

    /**
     * Sets the request leader.
     *
     * @param candidate The request candidate.
     * @return The poll request builder.
     * @throws IllegalArgumentException if {@code candidate} is not positive
     */
    public Builder withCandidate(NodeId candidate) {
      this.candidate = checkNotNull(candidate, "candidate cannot be null");
      return this;
    }

    /**
     * Sets the request last log index.
     *
     * @param logIndex The request last log index.
     * @return The poll request builder.
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public Builder withLogIndex(long logIndex) {
      checkArgument(logIndex >= 0, "logIndex must be positive");
      this.logIndex = logIndex;
      return this;
    }

    /**
     * Sets the request last log term.
     *
     * @param logTerm The request last log term.
     * @return The poll request builder.
     * @throws IllegalArgumentException if {@code term} is negative
     */
    public Builder withLogTerm(long logTerm) {
      checkArgument(logTerm >= 0, "logTerm must be positive");
      this.logTerm = logTerm;
      return this;
    }

    @Override
    protected void validate() {
      super.validate();
      checkArgument(term >= 0, "term must be positive");
      checkNotNull(candidate, "candidate cannot be null");
      checkArgument(logIndex >= 0, "logIndex must be positive");
      checkArgument(logTerm >= 0, "logTerm must be positive");
    }

    @Override
    public VoteRequest build() {
      validate();
      return new VoteRequest(term, candidate, logIndex, logTerm);
    }
  }
}