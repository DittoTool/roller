/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 */

package org.apache.roller.weblogger.pojos;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.util.Utilities;


/**
 * Base permission class for Roller. 
 */
public abstract class RollerPermission extends java.security.Permission {
    private static Log log = LogFactory.getLog(RollerPermission.class);
    

    public RollerPermission(String name) {
        super(name);
    }
            
    public abstract void setActions(String actions); 

    @Override
    public abstract String getActions();

    public List<String> getActionsAsList() {
        return Utilities.stringToStringList(getActions(), ",");
    }
    
    public void setActionsAsList(List<String> actionsList) {
        setActions(Utilities.stringListToString(actionsList, ","));
    }

    public boolean hasAction(String action) {
        Set<String> actionSet = new HashSet<>(getActionsAsList());
        return actionSet.contains(action);
    }
    
    public boolean hasActions(List<String> actionsToCheck) {
        Set<String> actionSet = new HashSet<>(getActionsAsList());
        for (String actionToCheck : actionsToCheck) {
            if (!actionSet.contains(actionToCheck)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Merge actions into this permission.
     */
    public void addActions(ObjectPermission perm) {
        List<String> newActions = perm.getActionsAsList();
        Set<String> updatedActionSet = new HashSet<>(updatedActions);
        for (String newAction : newActions) {
            if (!updatedActionSet.contains(newAction)) {
                updatedActions.add(newAction);
            }
        }
        setActionsAsList(updatedActions);
    }
    
    /**
     * Merge actions into this permission.
     */
    public void addActions(List<String> newActions) {
        Set<String> updatedActionSet = new HashSet<>(updatedActions);
        for (String newAction : newActions) {
            if (!updatedActionSet.contains(newAction)) {
                updatedActions.add(newAction);
            }
        }
        setActionsAsList(updatedActions);
    }
    
    /**
     * Merge actions into this permission.
     */
    public void removeActions(List<String> actionsToRemove) {
        List<String> updatedActions = getActionsAsList();
        for (String actionToRemove : actionsToRemove) {
            updatedActions.remove(actionToRemove);
        }
        log.debug("updatedActions2: " + updatedActions);
        setActionsAsList(updatedActions);
    }
    
    /**
     * True if permission specifies no actions
     */
    public boolean isEmpty() {
        return getActions() == null || getActions().isBlank();
    }
}
