/*
 * Copyright 2015 the original author or authors.
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
package net.kuujo.copycat.raft;

import net.kuujo.copycat.io.serializer.Serializer;
import net.kuujo.copycat.io.serializer.ServiceLoaderTypeResolver;
import net.kuujo.copycat.io.storage.Storage;
import net.kuujo.copycat.io.transport.Transport;
import net.kuujo.copycat.raft.state.ServerContext;
import net.kuujo.copycat.util.ConfigurationException;
import net.kuujo.copycat.util.Managed;
import net.kuujo.copycat.util.concurrent.Context;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Raft server.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class RaftServer implements Managed<RaftServer> {

  /**
   * Raft server state types.
   *
   * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
   */
  public enum State {

    /**
     * Start state.
     */
    INACTIVE,

    /**
     * Join state.
     */
    JOIN,

    /**
     * Leave state.
     */
    LEAVE,

    /**
     * Passive state.
     */
    PASSIVE,

    /**
     * Follower state.
     */
    FOLLOWER,

    /**
     * Candidate state.
     */
    CANDIDATE,

    /**
     * Leader state.
     */
    LEADER

  }

  /**
   * Returns a new Raft builder.
   *
   * @return A new Raft builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  private final ServerContext context;
  private CompletableFuture<RaftServer> openFuture;
  private CompletableFuture<Void> closeFuture;
  private boolean open;

  private RaftServer(ServerContext context) {
    this.context = context;
  }

  /**
   * Returns the current Raft term.
   *
   * @return The current Raft term.
   */
  public long term() {
    return context.getTerm();
  }

  /**
   * Returns the current Raft leader.
   *
   * @return The current Raft leader.
   */
  public Member leader() {
    return context.getLeader();
  }

  /**
   * Returns the server execution context.
   * <p>
   * The execution context is the event loop that this server uses to communicate other Raft servers.
   * Implementations must guarantee that all asynchronous {@link java.util.concurrent.CompletableFuture} callbacks are
   * executed on a single thread via the returned {@link net.kuujo.copycat.util.concurrent.Context}.
   * <p>
   * The {@link net.kuujo.copycat.util.concurrent.Context} can also be used to access the Raft server's internal
   * {@link net.kuujo.copycat.io.serializer.Serializer serializer} via {@link Context#serializer()}.
   *
   * @return The Raft context.
   */
  public Context context() {
    return context.getContext();
  }

  /**
   * Returns the Raft server state.
   *
   * @return The Raft server state.
   */
  public State state() {
    return context.getState();
  }

  @Override
  public CompletableFuture<RaftServer> open() {
    if (open)
      return CompletableFuture.completedFuture(this);

    if (openFuture == null) {
      synchronized (this) {
        if (openFuture == null) {
          if (closeFuture == null) {
            openFuture = context.open().thenApply(c -> {
              openFuture = null;
              open = true;
              return this;
            });
          } else {
            openFuture = closeFuture.thenCompose(v -> context.open().thenApply(c -> {
              openFuture = null;
              open = true;
              return this;
            }));
          }
        }
      }
    }
    return openFuture;
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public CompletableFuture<Void> close() {
    if (!open)
      return CompletableFuture.completedFuture(null);

    if (closeFuture == null) {
      synchronized (this) {
        if (closeFuture == null) {
          if (openFuture == null) {
            closeFuture = context.close().thenRun(() -> {
              closeFuture = null;
              open = false;
            });
          } else {
            closeFuture = openFuture.thenCompose(c -> context.close().thenRun(() -> {
              closeFuture = null;
              open = false;
            }));
          }
        }
      }
    }
    return closeFuture;
  }

  @Override
  public boolean isClosed() {
    return !open;
  }

  /**
   * Deletes the Raft server and its logs.
   *
   * @return A completable future to be completed once the server has been deleted.
   */
  public CompletableFuture<Void> delete() {
    return close().thenRun(context::delete);
  }

  /**
   * Raft server builder.
   */
  public static class Builder extends net.kuujo.copycat.util.Builder<RaftServer> {
    private static final Duration DEFAULT_RAFT_ELECTION_TIMEOUT = Duration.ofMillis(500);
    private static final Duration DEFAULT_RAFT_HEARTBEAT_INTERVAL = Duration.ofMillis(150);
    private static final Duration DEFAULT_RAFT_SESSION_TIMEOUT = Duration.ofMillis(5000);

    private Transport transport;
    private Storage storage;
    private Serializer serializer;
    private StateMachine stateMachine;
    private int memberId;
    private Members members;
    private Duration electionTimeout = DEFAULT_RAFT_ELECTION_TIMEOUT;
    private Duration heartbeatInterval = DEFAULT_RAFT_HEARTBEAT_INTERVAL;
    private Duration sessionTimeout = DEFAULT_RAFT_SESSION_TIMEOUT;

    private Builder() {
    }

    @Override
    protected void reset() {
      transport = null;
      storage = null;
      serializer = null;
      stateMachine = null;
      memberId = 0;
      members = null;
    }

    /**
     * Sets the server transport.
     *
     * @param transport The server transport.
     * @return The server builder.
     */
    public Builder withTransport(Transport transport) {
      this.transport = transport;
      return this;
    }

    /**
     * Sets the server member ID.
     *
     * @param memberId The server member ID.
     * @return The Raft builder.
     */
    public Builder withMemberId(int memberId) {
      this.memberId = memberId;
      return this;
    }

    /**
     * Sets the Raft members.
     * <p>
     * The provided set of members will be used to connect to the Raft cluster. The members list does not have to represent
     * the complete list of servers in the cluster, but it must have at least one reachable member.
     *
     * @param members The Raft members.
     * @return The Raft builder.
     */
    public Builder withMembers(Member... members) {
      if (members == null)
        throw new NullPointerException("members cannot be null");
      return withMembers(Arrays.asList(members));
    }

    /**
     * Sets the Raft members.
     * <p>
     * The provided set of members will be used to connect to the Raft cluster. The members list does not have to represent
     * the complete list of servers in the cluster, but it must have at least one reachable member.
     *
     * @param members The Raft members.
     * @return The Raft builder.
     */
    public Builder withMembers(Collection<Member> members) {
      return withMembers(Members.builder().withMembers(members).build());
    }

    /**
     * Sets the voting Raft members.
     *
     * @param members The voting Raft members.
     * @return The Raft builder.
     */
    public Builder withMembers(Members members) {
      this.members = members;
      return this;
    }

    /**
     * Sets the Raft serializer.
     *
     * @param serializer The Raft serializer.
     * @return The Raft builder.
     */
    public Builder withSerializer(Serializer serializer) {
      this.serializer = serializer;
      return this;
    }

    /**
     * Sets the storage module.
     *
     * @param storage The storage module.
     * @return The Raft server builder.
     */
    public Builder withStorage(Storage storage) {
      if (storage == null)
        throw new NullPointerException("storage cannot be null");
      this.storage = storage;
      return this;
    }

    /**
     * Sets the Raft state machine.
     *
     * @param stateMachine The Raft state machine.
     * @return The Raft builder.
     */
    public Builder withStateMachine(StateMachine stateMachine) {
      if (stateMachine == null)
        throw new NullPointerException("stateMachine cannto be null");
      this.stateMachine = stateMachine;
      return this;
    }

    /**
     * Sets the Raft election timeout, returning the Raft configuration for method chaining.
     *
     * @param electionTimeout The Raft election timeout duration.
     * @return The Raft configuration.
     * @throws IllegalArgumentException If the election timeout is not positive
     */
    public Builder withElectionTimeout(Duration electionTimeout) {
      if (electionTimeout == null)
        throw new NullPointerException("electionTimeout cannot be null");
      if (electionTimeout.isNegative() || electionTimeout.isZero())
        throw new IllegalArgumentException("electionTimeout must be positive");
      if (electionTimeout.toMillis() <= heartbeatInterval.toMillis())
        throw new IllegalArgumentException("electionTimeout must be greater than heartbeatInterval");
      this.electionTimeout = electionTimeout;
      return this;
    }

    /**
     * Sets the Raft heartbeat interval, returning the Raft configuration for method chaining.
     *
     * @param heartbeatInterval The Raft heartbeat interval duration.
     * @return The Raft configuration.
     * @throws IllegalArgumentException If the heartbeat interval is not positive
     */
    public Builder withHeartbeatInterval(Duration heartbeatInterval) {
      if (heartbeatInterval == null)
        throw new NullPointerException("sessionTimeout cannot be null");
      if (heartbeatInterval.isNegative() || heartbeatInterval.isZero())
        throw new IllegalArgumentException("sessionTimeout must be positive");
      if (heartbeatInterval.toMillis() >= electionTimeout.toMillis())
        throw new IllegalArgumentException("heartbeatInterval must be less than electionTimeout");
      this.heartbeatInterval = heartbeatInterval;
      return this;
    }

    /**
     * Sets the Raft session timeout, returning the Raft configuration for method chaining.
     *
     * @param sessionTimeout The Raft session timeout duration.
     * @return The Raft configuration.
     * @throws IllegalArgumentException If the session timeout is not positive
     */
    public Builder withSessionTimeout(Duration sessionTimeout) {
      if (sessionTimeout == null)
        throw new NullPointerException("sessionTimeout cannot be null");
      if (sessionTimeout.isNegative() || sessionTimeout.isZero())
        throw new IllegalArgumentException("sessionTimeout must be positive");
      if (sessionTimeout.toMillis() <= electionTimeout.toMillis())
        throw new IllegalArgumentException("sessionTimeout must be greater than electionTimeout");
      this.sessionTimeout = sessionTimeout;
      return this;
    }

    @Override
    public RaftServer build() {
      if (stateMachine == null)
        throw new ConfigurationException("state machine not configured");
      if (transport == null)
        throw new ConfigurationException("protocol not configured");
      if (members == null)
        throw new ConfigurationException("members not configured");
      if (storage == null)
        throw new ConfigurationException("storage not configured");

      // If no serializer instance was provided, create one.
      if (serializer == null) {
        serializer = new Serializer();
      }

      // Resolve serializer serializable types with the ServiceLoaderTypeResolver.
      serializer.resolve(new ServiceLoaderTypeResolver());

      ServerContext context = new ServerContext(memberId, members, transport, storage, stateMachine, serializer)
        .setHeartbeatInterval(heartbeatInterval)
        .setElectionTimeout(electionTimeout)
        .setSessionTimeout(sessionTimeout);
      return new RaftServer(context);
    }
  }

}
