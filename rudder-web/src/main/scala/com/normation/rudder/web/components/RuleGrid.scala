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

package com.normation.rudder.web.components

import com.normation.rudder.domain.policies._
import com.normation.rudder.services.policies.RuleTargetService
import com.normation.rudder.repository._
import com.normation.rudder.domain.nodes.NodeGroupId
import com.normation.rudder.domain.policies._
import net.liftweb.http.js._
import JsCmds._
import com.normation.rudder.services.reports.ReportingService
import com.normation.inventory.domain.NodeId
import com.normation.rudder.services.nodes.NodeInfoService
import JE._
import net.liftweb.common._
import net.liftweb.http._
import scala.xml._
import net.liftweb.util._
import net.liftweb.util.Helpers._
import com.normation.rudder.web.model._
import bootstrap.liftweb.LiftSpringApplicationContext.inject
import com.normation.utils.StringUuidGenerator
import com.normation.exceptions.TechnicalException
import com.normation.utils.Control.sequence
import com.normation.utils.HashcodeCaching
import com.normation.rudder.domain.log.RudderEventActor
import com.normation.cfclerk.domain.Technique
import com.normation.cfclerk.services.TechniqueRepository


object RuleGrid {
  def staticInit =     
    <head>
      <script type="text/javascript" language="javascript" src="/javascript/datatables/js/jquery.dataTables.js"></script>
      <style type="text/css">
        #actions_zone , .dataTables_length , .dataTables_filter {{ display: inline-block; }}
      </style>
    </head>
}

class RuleGrid(
    htmlId_rulesGridZone : String,
    rules : Seq[Rule],
    //JS callback to call when clicking on a line 
    detailsCallbackLink : Option[Rule => JsCmd],
    showCheckboxColumn:Boolean = true
) extends DispatchSnippet with Loggable {
  
  private[this] val targetInfoService = inject[RuleTargetService]
  private[this] val directiveRepository = inject[DirectiveRepository]
  private[this] val ruleRepository = inject[RuleRepository]

  private[this] val reportingService = inject[ReportingService]
  private[this] val nodeInfoService = inject[NodeInfoService]

  private[this] val htmlId_rulesGridId = "grid_" + htmlId_rulesGridZone

  private[this] val htmlId_reportsPopup = "popup_" + htmlId_rulesGridZone
  private[this] val htmlId_modalReportsPopup = "modal_" + htmlId_rulesGridZone
  private[this] val tableId_reportsPopup = "reportsGrid"
  
  private[this] val techniqueRepository = inject[TechniqueRepository] 
   
  def templatePath = List("templates-hidden", "reports_grid")
  def template() =  Templates(templatePath) match {
    case Empty | Failure(_,_,_) =>
      throw new TechnicalException("Template for report grid not found. I was looking for %s.html".format(templatePath.mkString("/")))
    case Full(n) => n
  }
  def reportTemplate = chooseTemplate("reports", "report", template)

  def dispatch = { 
    case "rulesGrid" => { _:NodeSeq => rulesGrid() }
  }
  
  def jsVarNameForId(tableId:String) = "oTable" + tableId
  
  def rulesGrid(linkCompliancePopup:Boolean = true) : NodeSeq = {
    (
        <div id={htmlId_rulesGridZone}>
          <div id={htmlId_modalReportsPopup} class="nodisplay">
            <div id={htmlId_reportsPopup} ></div>
          </div>
          <table id={htmlId_rulesGridId} cellspacing="0">
            <thead>
              <tr class="head">
                <th>Name<span/></th>
                <th>Description<span/></th>
                <th>Status<span/></th>
                <th>Deployment status<span/></th>
                <th>Compliance<span/></th>
                <th>Directive<span/></th>
                <th>Target node group<span/></th>
                { if(showCheckboxColumn) <th></th> else NodeSeq.Empty }
              </tr>
            </thead>
            <tbody>   
            {showRulesDetails(rules,linkCompliancePopup)}
            </tbody>
          </table> 
          <div class={htmlId_rulesGridId +"_pagination, paginatescala"} >
            <div id={htmlId_rulesGridId +"_paginate_area"}></div>
          </div>
        </div>
    ) ++ Script(
      JsRaw("""
        var #table_var#;
      """.replaceAll("#table_var#",jsVarNameForId(htmlId_rulesGridId))) &      
      //pop-ups for multiple Directives
      JsRaw( """var openMultiPiPopup = function(popupid) {
          createPopup(popupid,300,520);          
     }""") &
     OnLoad(JsRaw("""
      /* Event handler function */
      #table_var# = $('#%1$s').dataTable({
        "asStripClasses": [ 'color1', 'color2' ],
        "bAutoWidth": false,
        "bFilter" : true,
        "bPaginate" : true,
        "bLengthChange": true,
        "sPaginationType": "full_numbers",
        "bJQueryUI": false,
        "oLanguage": {
          "sZeroRecords": "No item involved"
        },
        "aaSorting": [[ 0, "asc" ]],
        "aoColumns": [ 
          { "sWidth": "95px" },
          { "sWidth": "95px"  },
          { "sWidth": "60px" },
          { "sWidth": "115px" },
          { "sWidth": "60px" },
          { "sWidth": "100px"  },
          { "sWidth": "100px" } %2$s
        ]
      });moveFilterAndFullPaginateArea('#%1$s'); 
      $("#%1$s_filter").insertAfter('#actions_zone');
      $("#%1$s_length").insertAfter('#actions_zone');
      createTooltip();""".format(
          htmlId_rulesGridId,
          { if(showCheckboxColumn) """, { "sWidth": "30px" }""" else "" }
      ).replaceAll("#table_var#",jsVarNameForId(htmlId_rulesGridId))
    )))
  }


  /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  
  private[this] def showRulesDetails(rules:Seq[Rule],linkCompliancePopup:Boolean) : NodeSeq = {
    sealed trait Line { val rule:Rule }
    
    case class OKLine(
        rule:Rule,
        compliance:Option[ComplianceLevel],
        trackerVariables: Seq[(Directive,ActiveTechnique,Technique)],
        target:Option[RuleTargetInfo]
    ) extends Line with HashcodeCaching
    
    case class ErrorLine(
        rule:Rule,
        trackerVariables: Box[Seq[(Directive,ActiveTechnique,Technique)]],
        target:Box[Option[RuleTargetInfo]]
    ) extends Line with HashcodeCaching 
    
    //is a rule applied for real ?
    def isApplied(
        rule:Rule,
        trackerVariables: Seq[(Directive,ActiveTechnique,Technique)],
        target:Option[RuleTargetInfo]
    ) : Boolean = {
      rule.isEnabled && target.isDefined && target.get.isEnabled && trackerVariables.size > 0 && 
      trackerVariables.forall { case (directive,activeTechnique,technique) => directive.isEnabled && activeTechnique.isEnabled }        
    }
    
    /*
     * For the Directive:
     * - if none defined => "None"
     * - if one define => <a href="Directive">Directive name</a>
     * - if more than one => <a href="Directive">Directive name</a>, ... + tooltip with the full list
     */
    
    def displayPis(seq:Seq[(Directive,ActiveTechnique,Technique)]) : NodeSeq = {
      def piLink(directive:Directive) = <a href={"""/secure/configurationManager/directiveManagement#{"directiveId":"%s"}""".format(directive.id.value)}>{
          directive.name + (if (directive.isEnabled) "" else " (disabled)")
        }</a>
      
      if(seq.size < 1) <i>None</i>
      else { 
        val popupId = Helpers.nextFuncName
        val tableId_listPI = Helpers.nextFuncName
        <span class="popcurs" onclick={"openMultiPiPopup('"+popupId+"') ; return false;"}>{seq.head._1.name + (if (seq.size > 1) ", ..." else "")}</span> ++
        <div id={popupId} class="nodisplay">
          <div class="simplemodal-title">
            <h1>List of Directives</h1>
            <hr/>
          </div>
          <div class="simplemodal-content">
            <br/>
            <h2>Click on a Directive name to go to its configuration screen</h2>
            <hr class="spacer"/>
            <br/>
            <br/>
            <table id={tableId_listPI} cellspacing="0">
              <thead>
                <tr class="head">
                 <th>Directive<span/></th>
                 <th>Technique<span/></th>
                </tr>
              </thead>
              <tbody>
            { 
              (
                "span" #> seq.map { case(directive,activeTechnique,technique) => "#link" #> <tr><td>{piLink(directive)}</td><td>{technique.name}</td></tr> }
              )(<span id="link"/>
              )
            }
              </tbody>
            </table>
            <hr class="spacer" />
          </div>
          <div class="simplemodal-bottom">
             <hr/>
             <div class="popupButton">
               <span>
                 <button class="simplemodal-close" onClick="return false;">
                   Close
                 </button>
               </span>
           </div>
         </div>
        </div> ++
        Script(OnLoad(JsRaw("""
          %1$s_tableId = $('#%2$s').dataTable({
            "asStripClasses": [ 'color1', 'color2' ],
            "bAutoWidth": false,
            "bFilter" : false,
            "bPaginate" : true,
            "bLengthChange": false,

            "bJQueryUI": false,
            "aaSorting": [[ 0, "asc" ]],
            "aoColumns": [
              { "sWidth": "200px" },
              { "sWidth": "300px" }
            ]
          });dropFilterAndPaginateArea('#%2$s');""".format( tableId_listPI, tableId_listPI))) )
      }
    }
    
    def displayTarget(target:Option[RuleTargetInfo]) = {
       target match {
            case None => <i>None</i>
            case Some(targetInfo) => targetInfo.target match {
              case GroupTarget(groupId) => <a href={ """/secure/assetManager/groups#{"groupId":"%s"}""".format(groupId.value)}>{ 
                  targetInfo.name + (if (targetInfo.isEnabled) "" else " (disabled)") 
                }</a>
              case _ => Text({ targetInfo.name + (if (targetInfo.isEnabled) "" else " (disabled)") })
             }
          }
    }
    {
      
    }
    //for each rule, get all the required info and display them
    val lines:Seq[Line] = rules.map { rule =>

      val trackerVariables: Box[Seq[(Directive,ActiveTechnique,Technique)]] = 
        sequence(rule.directiveIds.toSeq) { id =>
          directiveRepository.getDirective(id) match {
            case Full(directive) => directiveRepository.getActiveTechnique(id) match {
              case Full(activeTechnique) => techniqueRepository.getLastTechniqueByName(activeTechnique.techniqueName) match {
                case None => Failure("Can not find Technique for activeTechnique with name %s referenced in Rule with ID %s".format(activeTechnique.techniqueName, rule.id))
                case Some(technique) => Full((directive,activeTechnique,technique))
              }
              case e:EmptyBox => //it's an error if the directive ID is defined and found but it is not attached to an activeTechnique
                val error = e ?~! "Can not find Active Technique for Directive with ID %s referenced in Rule with ID %s".format(id, rule.id)
                logger.debug(error.messageChain, error)
                error
            }
            case e:EmptyBox => //it's an error if the directive ID is defined and such directive is not found
              val error = e ?~! "Can not find Directive with ID %s referenced in Rule with ID %s".format(id, rule.id)
              logger.debug(error.messageChain, error)
              error
          }
        }
      val targetInfo = rule.target match {
          case None => Full(None)
          case Some(target) => targetInfoService.getTargetInfo(target) match {
            case Full(targetInfo) => Full(Some(targetInfo))
            case Empty => 
              val m = "Can not find requested target: '%s', it seems to be a database inconsistency.".format(target.target)
              logger.debug(m)
              Failure(m)             
            case f:Failure => //it's an error if the directive ID is defined and such id is not found
              val error = f ?~! "Can not find Target information for target %s referenced in Rule with ID %s".format(target.target, rule.id)
              logger.debug(error.messageChain, error)
              error
          }
        }
      
      (trackerVariables,targetInfo) match {
        case (Full(seq), Full(target)) => 
          val compliance = if(isApplied(rule, seq, target)) computeCompliance(rule) else None
          OKLine(rule, compliance, seq, target)
        case (x,y) =>
          //the Rule has some error, try to disactivate it
          ruleRepository.update(rule.copy(isEnabledStatus=false),RudderEventActor) 

          ErrorLine(rule,x,y)
      }
    }
    

    //now, build html lines
    if(lines.isEmpty) {
      NodeSeq.Empty
    } else {
      lines.map { l => l match {
      case line:OKLine =>
        <tr>
          <td>{ // NAME 
            detailsLink(line.rule, line.rule.name)
          }</td>
          <td>{ // DESCRIPTION
            detailsLink(line.rule, line.rule.shortDescription)
          }</td>
          <td>{ // OWN STATUS
            if (line.rule.isEnabledStatus) "Enabled" else "Disabled"
          }</td>
          <td><b>{ // EFFECTIVE STATUS
            if(isApplied(line.rule, line.trackerVariables, line.target)) Text("In application")
            else {
                val conditions = Seq(
                    (line.rule.isEnabled, "Rule disabled" ), 
                    ( line.trackerVariables.size > 0, "No Directive defined"),
                    ( line.target.isDefined && line.target.get.isEnabled, "Group disabled")
                 ) ++ line.trackerVariables.flatMap { case (directive, activeTechnique,technique) => Seq(
                    ( directive.isEnabled , "Directive " + directive.name + " disabled") , 
                    ( activeTechnique.isEnabled, "Technique for '" + directive.name + "' disabled") 
                 )}
               
                val why =  conditions.collect { case (ok, label) if(!ok) => label }.mkString(", ") 
                <span class="tooltip tooltipable" tooltipid={line.rule.id.value}>Not applied</span>
                 <div class="tooltipContent" id={line.rule.id.value}><h3>Reason(s)</h3><div>{why}</div></div>
            }
          }</b></td>
          <td>{ //  COMPLIANCE
            buildComplianceChart(line.compliance, line.rule, linkCompliancePopup)
          }</td>
          <td>{ //  Directive: <not defined> or PIName [(disabled)]
            displayPis(line.trackerVariables)
           }</td>
          <td>{ //  TARGET NODE GROUP
            displayTarget(line.target)
          }</td>
          { // CHECKBOX 
            if(showCheckboxColumn) <td><input type="checkbox" name={line.rule.id.value} /></td> else NodeSeq.Empty 
          }
        </tr>
          
      case line:ErrorLine =>
        <tr class="error">
          <td>{ // NAME 
            detailsLink(line.rule, line.rule.name)
          }</td>
          <td>{ // DESCRIPTION
            detailsLink(line.rule, line.rule.shortDescription)
          }</td>
          <td>{ // OWN STATUS
            "N/A"
          }</td>
          <td>{ // EFFECTIVE STATUS
            "N/A"
          }</td>
          <td>{ //  COMPLIANCE
            "N/A"
          }</td>
          <td>{ //  Directive: <not defined> or PIName [(disabled)]
            line.trackerVariables.map(displayPis _).getOrElse("ERROR")
           }</td>
          <td>{ //  TARGET NODE GROUP
            line.target.map(displayTarget(_)).getOrElse("ERROR")
          }</td>
          { // CHECKBOX 
            if(showCheckboxColumn) <td><input type="checkbox" name={line.rule.id.value} /></td> else NodeSeq.Empty 
          }
        </tr>  
      } }
    } 
  }

  private[this] def computeCompliance(rule: Rule) : Option[ComplianceLevel] = {
    reportingService.findImmediateReportsByRule(rule.id) match {
      case None => Some(Applying) // when we have a rule but nothing in the database, it means that it is currentluy being deployed
      case Some(x) if (x.expectedNodeIds.size==0) => None
      case Some(x) if (x.getPendingNodeIds.size>0) => Some(Applying)
      case Some(x) =>  Some(new Compliance((100 * x.getSuccessNodeIds.size) / x.expectedNodeIds.size))
    }
  }

  private[this] def detailsLink(rule:Rule, text:String) : NodeSeq = {
    detailsCallbackLink match {
      case None => Text(text)
      case Some(callback) => SHtml.a( () => callback(rule), Text(text))
    }
  }  
  
  private[this] def buildComplianceChart(level:Option[ComplianceLevel], rule: Rule, linkCompliancePopup:Boolean) : NodeSeq = {
    level match {
      case None => Text("N/A")
      case Some(Applying) => Text("Applying")
      case Some(NoAnswer) => Text("No answer")
      case Some(Compliance(percent)) =>  {
        val text = Text(percent.toString + "%")
        if(linkCompliancePopup) SHtml.a({() => showPopup(rule)}, text)
        else text
      }
    }
  }

/*********************************************
  Popup for the reports
 ************************************************/
  private[this] def createPopup(rule: Rule) : NodeSeq = {
    val batch = reportingService.findImmediateReportsByRule(rule.id)

    <div class="simplemodal-title">
      <h1>List of nodes having the {Text(rule.name)} Rule</h1>
      <hr/>
    </div>
    <div class="simplemodal-content"> { bind("lastReportGrid",reportTemplate,
        "crName" -> Text(rule.name),
        "lines" -> (
          batch match {
            case None => Text("No Reports")
            case Some(reports) =>
            ((reports.getSuccessNodeIds().map ( x =>  ("Success" , x)) ++
               reports.getRepairedNodeIds().map ( x => ("Repaired" , x)) ++
               //reports.getWarnNode().map ( x => ("Warn" , x)) ++
               reports.getErrorNodeIds().map ( x =>  ("Error" , x)) ++
               reports.getPendingNodeIds().map ( x =>  ("Applying" , x)) ++
               reports.getNoReportNodeIds().map ( x => ("No answer" , x)) ) ++
               reports.getUnknownNodeIds().map ( x =>  ("Unknown" , x)) :Seq[(String, NodeId)]).flatMap {
                 case s@(severity:String, uuid:NodeId) if (uuid != null) =>
                   nodeInfoService.getNodeInfo(uuid) match {
                     case Full(nodeInfo)  => {
                        <tr class={severity.replaceAll(" ", "")}>
                        {bind("line",chooseTemplate("lastReportGrid","lines",reportTemplate),
                         "hostname" -> <a href={"""secure/assetManager/searchNodes#{"nodeId":"%s"}""".format(uuid.value)}><span class="curspoint" jsuuid={uuid.value.replaceAll("-","")} serverid={uuid.value}>{nodeInfo.hostname}</span></a>,
                         "severity" -> severity )}
                        </tr>
                     }
                     case x:EmptyBox => 
                       logger.error( (x?~! "An error occured when trying to load node %s".format(uuid.value)),x)
                       <div class="error">Node with ID "{uuid.value}" is invalid</div>
                   }

               }
            }
         )
      )
    }<hr class="spacer" />
    </div>
    <div class="simplemodal-bottom">
      <hr/>
      <div class="popupButton">
        <span>
          <button class="simplemodal-close" onClick="return false;">
          Close
          </button>
        </span>
      </div>
    </div>

  }
  
  private[this] def showPopup(rule: Rule) : JsCmd = {
    val popupHtml = createPopup(rule)
    SetHtml(htmlId_reportsPopup, popupHtml) &
    JsRaw("""
        var #table_var#;
        /* Formating function for row details */
        function fnFormatDetails ( id ) {
          var sOut = '<div id="'+id+'" class="reportDetailsGroup"/>';
          return sOut;
        }
      """.replaceAll("#table_var#",jsVarNameForId(tableId_reportsPopup))
    ) & OnLoad(
        JsRaw("""
          /* Event handler function */
          #table_var# = $('#%1$s').dataTable({
            "bAutoWidth": false,
            "bFilter" : false,
            "bPaginate" : true,
            "bLengthChange": false,
            "sPaginationType": "full_numbers",
            "bJQueryUI": false,
            "aaSorting": [[ 3, "asc" ]],
            "aoColumns": [
              { "sWidth": "200px" },
              { "sWidth": "300px" }
            ]
          });moveFilterAndFullPaginateArea('#%1$s');""".format( tableId_reportsPopup).replaceAll("#table_var#",jsVarNameForId(tableId_reportsPopup))
        ) //&  initJsCallBack(tableId)
    ) &
    JsRaw( """ createPopup("%s",300,500)
     """.format(htmlId_modalReportsPopup))

  }

}



trait ComplianceLevel 


case object Applying extends ComplianceLevel
case object NoAnswer extends ComplianceLevel
case class Compliance(val percent:Int) extends ComplianceLevel with HashcodeCaching 

  
  

