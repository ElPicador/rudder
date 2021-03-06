/*
*************************************************************************************
* Copyright 2011 Normation SAS
*************************************************************************************
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as
* published by the Free Software Foundation, either version 3 of the
* License, or (at your option) any later version.
*
* In accordance with the terms of section 7 (7. Additional Terms.) of
* the GNU Affero GPL v3, the copyright holders add the following
* Additional permissions:
* Notwithstanding to the terms of section 5 (5. Conveying Modified Source
* Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU Affero GPL v3
* licence, when you create a Related Module, this Related Module is
* not considered as a part of the work and may be distributed under the
* license agreement of your choice.
* A "Related Module" means a set of sources files including their
* documentation that, without modification of the Source Code, enables
* supplementary functions or services in addition to those offered by
* the Software.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Affero General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/agpl.html>.
*
*************************************************************************************
*/

package com.normation.rudder.domain


import com.normation.inventory.domain.NodeId
import com.normation.rudder.domain.policies.{DirectiveId, RuleId}
import com.normation.rudder.domain.nodes.NodeGroupId
import com.normation.cfclerk.domain.TechniqueId

import org.joda.time.Duration

object Constants {    

  //non random Policy Instance Id
  def buildHasPolicyServerGroupId(policyServerId:NodeId) = 
    NodeGroupId("hasPolicyServer-" + policyServerId.value )
 
    
  val ROOT_POLICY_SERVER_ID = NodeId("root")  
  
  /**
   * For the given policy server, what is the ID of its 
   * "distributePolicy" instance ?
   */
  def buildCommonDirectiveId(policyServerId:NodeId) = 
    DirectiveId("common-" + policyServerId.value)
  
  /////////// Policy Node: DistributePolicy policy instance variable //////////
  val V_ALLOWED_NETWORK = "ALLOWEDNETWORK"
    
  /**
   * The lapse of time when we consider that the CR is still pending
   * Let's say 10 minutes 
   */
  val pendingDuration = new Duration(10*1000*60)
  
  val PTLIB_MINIMUM_UPDATE_INTERVAL = 60 //in seconds
  
  val DYNGROUP_MINIMUM_UPDATE_INTERVAL = 1 //in minutes
  
  
  val XML_FILE_FORMAT_1_0 = "1.0"
  val XML_FILE_FORMAT_2_0 = "2.0"
    
    
  val CONFIGURATION_RULES_ARCHIVE_TAG = "#rules-archive"
  val GROUPS_ARCHIVE_TAG = "#groups-archive" 
  val POLICY_LIBRARY_ARCHIVE_TAG = "#directives-archive"
  val FULL_ARCHIVE_TAG = "#full-archive"
    
}
