/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.dependency;

import org.apache.maven.artifact.Artifact;

import java.util.Collections;
import java.util.List;

/**
 * A node of the dependency graph of a project: its artifact, with the scope and the optional flag of the dependency it
 * comes from, and its children. The root stands for the project and has the project's artifact.
 */
public class DependencyNode {

    private final DependencyNode parent;

    private final Artifact artifact;

    private List<DependencyNode> children = Collections.emptyList();

    DependencyNode(final DependencyNode parent, final Artifact artifact) {
        this.parent = parent;
        this.artifact = artifact;
    }

    /**
     * @param children the children, a list that nothing else changes afterwards
     */
    void setChildren(final List<DependencyNode> children) {
        this.children = Collections.unmodifiableList(children);
    }

    /**
     * Visits this node and, if {@link DependencyNodeVisitor#visit(DependencyNode)} returns {@code true}, its children
     * in order, stopping at the first child whose visit returns {@code false}; then ends the visit of this node.
     *
     * @param visitor the visitor
     * @return the result of {@link DependencyNodeVisitor#endVisit(DependencyNode)} for this node
     */
    public boolean accept(final DependencyNodeVisitor visitor) {
        if (visitor.visit(this)) {
            for (final DependencyNode child : children) {
                if (!child.accept(visitor)) {
                    break;
                }
            }
        }
        return visitor.endVisit(this);
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public List<DependencyNode> getChildren() {
        return children;
    }

    public DependencyNode getParent() {
        return parent;
    }

    /**
     * @return the artifact, followed by {@code (optional)} for an optional dependency
     */
    public String toNodeString() {
        return artifact + (artifact.isOptional() ? " (optional)" : "");
    }

    @Override
    public String toString() {
        return toNodeString();
    }
}
