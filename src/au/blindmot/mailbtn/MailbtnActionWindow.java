/******************************************************************************
 * Product: iDempiere Free ERP Project based on Compiere (2006)               *
 * Copyright (C) 2018 Phil Barnett All Rights Reserved.                     *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 *  FOR NON-COMMERCIAL DEVELOPER USE ONLY                                     *
 *  @author Phil Barnett  - philbarnett72@gmail.com                       *
 *****************************************************************************/


package au.blindmot.mailbtn;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import javax.activation.DataSource;

import org.adempiere.exceptions.DBException;
import org.adempiere.webui.LayoutUtils;
import org.adempiere.webui.adwindow.ADWindow;
import org.adempiere.webui.adwindow.ADWindowContent;
import org.adempiere.webui.adwindow.AbstractADWindowContent;
import org.adempiere.webui.apps.AEnv;
import org.adempiere.webui.component.Checkbox;
import org.adempiere.webui.component.Column;
import org.adempiere.webui.component.Columns;
import org.adempiere.webui.component.ConfirmPanel;
import org.adempiere.webui.component.Grid;
import org.adempiere.webui.component.GridFactory;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.ListItem;
import org.adempiere.webui.component.Listbox;
import org.adempiere.webui.component.Panel;
import org.adempiere.webui.component.Row;
import org.adempiere.webui.component.Rows;
import org.adempiere.webui.component.Window;
import org.adempiere.webui.event.DialogEvents;
import org.adempiere.webui.util.ZKUpdateUtil;
import org.adempiere.webui.window.FDialog;
import org.adempiere.webui.window.WEMailDialog;
import org.apache.commons.net.ntp.TimeStamp;
import org.compiere.model.GridTab;
import org.compiere.model.MAttachment;
import org.compiere.model.MClient;
import org.compiere.model.MMailText;
import org.compiere.model.MUser;
import org.compiere.model.MUserMail;
import org.compiere.util.ByteArrayDataSource;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.EMail;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Space;
import org.zkoss.zul.Tabpanel;
import org.zkoss.zul.Vbox;



public class MailbtnActionWindow implements EventListener<Event>

{
	private static CLogger log = CLogger.getCLogger(MailbtnActionWindow.class);
	
	private AbstractADWindowContent panel;
	
	private Window 			mailTemplateSelect = null;
	private ConfirmPanel 	confirmPanel = new ConfirmPanel(true);
	private Listbox 		cbotemplateList = new Listbox();
	private Listbox 		cboBPUserList = new Listbox();
	private Checkbox		chkStatus = new Checkbox();
	private int				m_AD_User_ID = -1;
	private MMailText		m_MailText = null;
	private int 			r_mailtext_id = 0;
	private int				m_AD_Window_ID = 0;
	private int				m_Tab_id = 0;
	private GridTab 		tab = null;
	private Label 			userLable = null;
	private boolean 		isBP = false;
	private MClient			m_client = null;
	private Integer 		bpuser = null;
	private WEMailDialog	dialog = null;
	private MUser			m_user = null;	
	private List<List<Object>>	templateRaw = new ArrayList<List<Object>>();
	private ADWindow window;
	
	public MailbtnActionWindow(AbstractADWindowContent panel, ADWindow window) {
		this.panel = panel;	
		this.window = window;
		prepare();
	
	}
	
	public void prepare() {
	
		ADWindowContent content = window.getADWindowContent();
		tab = content.getActiveGridTab();
		log.info("MailbtnAction window title: " + window.getTitle());
		System.out.println (Env.getAD_Client_ID(Env.getCtx()));
		m_AD_Window_ID = tab.getAD_Window_ID();
		m_Tab_id = tab.getAD_Tab_ID();
		
		log.info("--------" + "Record ID: " + tab.getRecord_ID() + "....Window Id: " + tab.getAD_Window_ID() + "......Table ID: " + tab.getAD_Table_ID() + "......AD_User" + tab.get_ValueAsString("AD_User_ID"));
		log.info("--------" + "Table name: " + tab.getTableName());
		
		//Get the templates for the logged in user and print to console
				Integer sqlPara = new Integer(Env.getAD_Client_ID(Env.getCtx()));
				StringBuilder sql = new StringBuilder();
				sql.append("SELECT r_mailtext_id, name FROM r_mailtext WHERE ad_client_id=? ");
				sql.append("AND name NOT LIKE '%email signature'");
				List<KeyNamePair> templates = getKeyNamePair(sql.toString(), sqlPara);
		log.info(templates.toString());
		
		userLable = new Label(Msg.translate(Env.getCtx(), "ad_user_id"));
		
	}
	
	/**
	 * 
	 */
	public void show() {
		
	if (tab.getAD_Window_ID()==123){//It's Business Partner window. Get Users for this BP.
			
			isBP = true;
			bpuser = new Integer(tab.get_ValueAsString("C_BPartner_ID"));
			StringBuilder sqlquery = new StringBuilder();
			sqlquery.append("SELECT ad_user_id, name from ad_user WHERE c_bpartner_id=?");
			
			cboBPUserList.setMold("select");
			cboBPUserList.getItems().clear();
			
			List<KeyNamePair> knp = getKeyNamePair(sqlquery.toString(), bpuser);
			
			for (KeyNamePair templateDrop : knp){
				
				cboBPUserList.appendItem(templateDrop.getName(), templateDrop.getKey());
			if (cboBPUserList.getItemCount() > 0)
				cboBPUserList.setSelectedIndex(0);
			
			}
			
	}
	else
	{
		isBP = false;
	}
		
		if (mailTemplateSelect != null)mailTemplateSelect=null;
		{	
		mailTemplateSelect = new Window();
		ZKUpdateUtil.setWidth(mailTemplateSelect, "450px");
		mailTemplateSelect.setClosable(true);
		mailTemplateSelect.setBorder("normal");
		mailTemplateSelect.setStyle("position:absolute");
		mailTemplateSelect.addEventListener("onValidate", this);
		
		cbotemplateList.setMold("select");
		cbotemplateList.getItems().clear();
		
		//Get the templates for the logged in user
		Integer sqlPara = new Integer(Env.getAD_Client_ID(Env.getCtx()));
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT r_mailtext_id, name FROM r_mailtext WHERE ad_client_id=?");
		sql.append(" AND r_mailtext.name NOT LIKE '%email signature'");
		
		List<KeyNamePair> kvl = getKeyNamePair(sql.toString(), sqlPara);
		
		for (KeyNamePair templateDrop1 : kvl){
				
			cbotemplateList.appendItem(templateDrop1.getName(), templateDrop1.getKey());
		if (cbotemplateList.getItemCount() > 0)
			cbotemplateList.setSelectedIndex(0);
		}
		cbotemplateList.appendItem("Blank email", 1);//Add a blank email so a normal email can be sent.
		
		/*Check if this is the Business Partner window (AD_Window_ID=123). If it is:
		 * We want the window to show a list of the first say 20 names of users that are linked to the BP in context.
		 */
		
		
		Vbox vb = new Vbox();
		ZKUpdateUtil.setWidth(vb, "100%");
		mailTemplateSelect.appendChild(vb);
		mailTemplateSelect.setSclass("toolbar-popup-window");
		vb.setSclass("toolbar-popup-window-cnt");
		vb.setAlign("stretch");
		
		Grid grid = GridFactory.newGridLayout();
		vb.appendChild(grid);
        
        Columns columns = new Columns();
        Column column = new Column();
        ZKUpdateUtil.setHflex(column, "min");
        columns.appendChild(column);
        column = new Column();
        ZKUpdateUtil.setHflex(column, "1");
        columns.appendChild(column);
        grid.appendChild(columns);
        
        Rows rows = new Rows();
		grid.appendChild(rows);
		
		//Add the template list
		Row row = new Row();
		rows.appendChild(row);
		row.appendChild(new Label(Msg.translate(Env.getCtx(), "r_mailtext_id"))); //This translate() method is pretty cool. Such a big codebase to work with.
		row.appendChild(cbotemplateList);
		ZKUpdateUtil.setHflex(cbotemplateList, "1");
		cbotemplateList.addEventListener(Events.ON_SELECT, this);
		
		if(isBP){
			
			//Add the BP user list
			
			row = new Row();
			rows.appendChild(row);
			row.appendChild(userLable);
			row.appendChild(cboBPUserList);
			ZKUpdateUtil.setHflex(cboBPUserList, "1");
			cboBPUserList.addEventListener(Events.ON_SELECT, this);
		}
		
		//Add the chkStatus checkbox, which was created as a 'hot spare'.
		Panel panel = new Panel();
		panel.appendChild(chkStatus);
		ZKUpdateUtil.setHflex(chkStatus, "min");
		panel.appendChild(new Space());
		chkStatus.addEventListener(Events.ON_CHECK, this);
		
		chkStatus.setLabel("Spare Check Box");
		chkStatus.setSelected(false);
		chkStatus.setVisible(false);//Not required at the moment.
		
		row = new Row();
		rows.appendChild(row);
		row.appendChild(new Space());
		row.appendChild(panel);		m_AD_Window_ID = 0;

		vb.appendChild(confirmPanel);
		LayoutUtils.addSclass("dialog-footer", confirmPanel);
		confirmPanel.addActionListener(this);
		
		}
		
		
		LayoutUtils.openPopupWindow(panel.getToolbar().getButton("mail"), mailTemplateSelect, "after_start");
	
	}
	
	/**
	 * @see org.zkoss.zk.ui.event.EventListener#onEvent(org.zkoss.zk.ui.event.Event)
	 */
	@Override
	public void onEvent(Event event) throws Exception {
		
		if(event.getTarget().getId().equals(ConfirmPanel.A_CANCEL))
			mailTemplateSelect.onClose();
		if(event.getTarget() == dialog && event.getName() == "onWindowClose"){
			TimeStamp timeMailDialogClose = new TimeStamp(System.currentTimeMillis());
			if(m_AD_User_ID > 1) fixMuserMailrecord(timeMailDialogClose);
		
		}
		
		else if(event.getTarget().getId().equals(ConfirmPanel.A_OK)) {
			
			mailTemplateSelect.setVisible(false);
			Clients.showBusy(Msg.getMsg(Env.getCtx(), "Processing"));
			Events.echoEvent("onValidate", mailTemplateSelect, null);	
		}
			
			else if(event.getTarget() == cbotemplateList)
			{
			
			ListItem li = cbotemplateList.getSelectedItem();//TODO: Move the list items select etc to onValidate() - see ReportAction.java and replicate. 
				if (li != null && li.getValue() != null)
				{
					r_mailtext_id = Integer.valueOf(li.getValue().toString());
					log.info("r_mailtext_id = " + r_mailtext_id);
					//chkAllColumns.setVisible(AD_PrintFormat_ID == -1); //Shows or hides a checkbox chkAllColumns based on ListBox cbotemplateList values
				}
			}
				
				if(isBP && event.getTarget() == cboBPUserList)
				{
				ListItem li2 = cboBPUserList.getSelectedItem();
				if (li2 != null && li2.getValue() != null)
				{
					m_AD_User_ID = Integer.valueOf(li2.getValue().toString());
					log.info("MailbtnAction.m_AD_User_ID = " + m_AD_User_ID);
				}
			
		}
		
		else if(event.getTarget() == chkStatus)
			log.info("MailbtnAction.chkStatus" + chkStatus.toString());
			//cboExportType.setVisible(chkStatus.isChecked()); TODO put some action here for the 'chkStatus' box
		
		else if (event.getName().equals("onValidate")) {
			try {
				validate();
			} finally {
				Clients.clearBusy();
				panel.getComponent().invalidate();
			}
		}
		
	}
	
	/**
	 * @throws Exception
	 */
	private void validate() throws Exception
	{
		ListItem li = cbotemplateList.getSelectedItem();
		if(li == null || li.getValue() == null)
		{
			FDialog.error(0, mailTemplateSelect, "EmailTemplateMandatory");
			return;
		}
		
		else r_mailtext_id = Integer.valueOf(li.getValue().toString());
		
		if (!isBP && tab.get_ValueAsString("AD_User_ID") == "" )
		{
			FDialog.error(0, mailTemplateSelect, "No User found to email to. Check configuration: the MailButton plugin requires that the button is only visible for windows based on the ad_user and c_bpartner tables.");
			return;
		
		}
		
		if(isBP)
		{
			ListItem li2 = cboBPUserList.getSelectedItem();
			m_AD_User_ID = Integer.valueOf(li2.getValue().toString());
		}	
		
		boolean status = chkStatus.isChecked();	//TODO This	code block may need to be deleted if not used. 
		if (status)
		{
			
			//TODO put some contextual validation here for the 'chkStatus' box.
			{
				FDialog.error(0, mailTemplateSelect, "The Status box is causing grief");
				return;
			}
		}
		else
		{
			email();
		
		}
	}
	
	private void email() throws Exception
	{
		mailTemplateSelect.onClose();	
		//TODO
		cmd_sendMail();
		Tabpanel tabPanel = (Tabpanel) panel.getComponent().getParent();
		tabPanel.getLinkedTab().setSelected(true);
		
	}
	
	private void cmd_sendMail() throws Exception{
		
		MAttachment m_attachment = null;
		
		if (log.isLoggable(Level.INFO)) log.info("R_MailText_ID=" + r_mailtext_id);
		
		if(r_mailtext_id != 1) 
		{
			m_MailText = new MMailText (Env.getCtx(), r_mailtext_id, null);
		if (m_MailText.getR_MailText_ID() == 0)
		{
			throw new Exception ("Not found @R_MailText_ID@=" + r_mailtext_id);
		}
		
		else
		{
			if(isBP && bpuser!=null)
			{
				m_MailText.setBPartner(bpuser.intValue());
			}
		}
		}
		
		/*
		 * If it isn't a BP window, then get AD_User_ID from tab. If it is a BP, then m_AD_User_ID should already contain a valid value.
		 * MMailText (Properties ctx, ResultSet rs, String trxName)
		 */
		
		if(m_MailText == null) m_MailText = new MMailText (Env.getCtx(), null, null);
		
		if(!isBP){
			
		String ms_AD_User_ID;
		ms_AD_User_ID = tab.get_ValueAsString("AD_User_ID");
		Integer i = new Integer(ms_AD_User_ID);
		m_AD_User_ID = i.intValue();
		
		}
		if (log.isLoggable(Level.INFO)) log.info("m_AD_User_ID " + m_AD_User_ID);
		m_MailText.setUser(m_AD_User_ID);//m_AD_User_ID should already contain a valid value.
		
		if (m_AD_User_ID == -1){
			
			FDialog.error(0, mailTemplateSelect, "Can't resolve email recipient, please enter manually in email window");
		}
			
		
		Properties ctx = Env.getCtx();
		m_user = new MUser (ctx, m_AD_User_ID, null); 
		
		String to;
		
		try 
		{
			to = m_user.getInternetAddress().toString();
		} catch(Exception ex) {
			FDialog.warn(0, "Can't resolve email recipient, please enter manually in email window","Warning");
			to = "<email_required>";
		}
		
		MUser from = MUser.get(Env.getCtx(), Env.getAD_User_ID(Env.getCtx()));
	
		String subject = "";
		String message = ""; 
		
		if(r_mailtext_id != 1) //We don't want a blank email.
		{
			subject = m_MailText.getMailHeader();
			message = m_MailText.getMailText(true);//'true' parameter gets all mail text fields from the MMailText object.
			m_attachment = m_MailText.getAttachment();
		}
		
		dialog = new WEMailDialog (Msg.getMsg(Env.getCtx(), "SendMail"),
			from, to, subject, message, null);
		dialog.addEventListener(DialogEvents.ON_WINDOW_CLOSE, this);
		AEnv.getDialogHeader(Env.getCtx(), tab.getWindowNo());
		
		
		
		m_client = MClient.get(Env.getCtx());
		//Get attachments
		if (m_attachment != null){ //Skip the entire attachment part of the code if there's no attachments.
		int attchCount = m_attachment.getEntryCount();
		
		for(int j=0; j<attchCount; j++)
			{
			String fileName = /*System.getProperty("java.io.tmpdir") + System.getProperty("file.separator") + */ m_attachment.getEntryName(j);
			
			File tempFile = new File(fileName);
			InputStream is =new FileInputStream(m_attachment.getEntryFile(j, tempFile));
			
			DataSource dataSource= new ByteArrayDataSource(is,null).setName(fileName);
			//dataSource.setName(fileName);
		
			dialog.setAttachment(dataSource);
	
			} 
		}//end if
		try
		{
			AEnv.showWindow(dialog);
			
			
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, "", e);
		}
			
			//		cmd_sendMail
	}
		
			/**
			 *MUserMail adds a record of the mail sent to the Usermail tab of the User window.
			 * A redundant record in the SENDER's User mail record is created by the above code when WEMailDialog is called.
			 * The below code creates a record in the RECIPIENT's User mail record
			 * When the WEDialog calls DialogEvents.ON_WINDOW_CLOSE, we don't know if the mail was successful or not
			 * -Get the last record in the ad_usermail table.
			 * -Compare the timestamp in the ad_usermail table with the code created timestamp.
			 * -If they're within an appropriate time, then ASSUME it's the correct record.
			 * -Create a musermail object based on the selected ID, update the mmailtextid and set the ad_userid to the recipient, 
			 * -insert the mailtext from dialog.getMessage().
			 *@param timestamp
			 */

		private void fixMuserMailrecord(TimeStamp timestamp)
		{
			StringBuilder sql = new StringBuilder();
			sql.append("SELECT ad_usermail_id, updated FROM ad_usermail ORDER BY ad_usermail_id DESC");
			//sql.append(" LIMIT 1");//Limit will be 1, we want the last record. Note, limit doesn't work in Idempiere
			
			int musermail_id =0;
			Timestamp  updated = null;
			TimeStamp lastMUserupdate = null;
			TimeStamp  sentTimestamp = timestamp;
			
			
			
		
			 PreparedStatement pstmt = null;
			 ResultSet rs = null;
			 try
			 {
			      pstmt = DB.prepareStatement(sql.toString(), null);
			      DB.setParameters(pstmt, new Object[]{});
			      rs = pstmt.executeQuery();
			     
			      while (rs.getRow()==0) {//we want just the first row.
			    	  rs.next();  
			      musermail_id = rs.getInt(1);
			      updated = rs.getTimestamp(2);
			      lastMUserupdate = new TimeStamp(updated.getTime());
			      
			      }
			 }
			 // If your method is not throwing Exception or SQLException you need this block to catch SQLException
			 // and convert them to unchecked DBException
			 catch (SQLException e)
			 {
			      throw new DBException(e, sql.toString());
			 }
			 // '''ALWAYS''' close your ResultSet in a finally statement
			 finally
			 {
			      DB.close(rs, pstmt);
			      rs = null; pstmt = null;
			 }
			
			MUserMail lastMUserMailrec = new MUserMail(Env.getCtx(), musermail_id, null);
			log.info(lastMUserMailrec.getSubject());
			log.info(lastMUserMailrec.getMessageID());
			log.info("Usermail sent time: " + lastMUserupdate.ntpValue());
			log.info("System time when 'OK' was clicked in mail dialog: " + sentTimestamp.ntpValue());
			long ntpdiff = sentTimestamp.ntpValue() - lastMUserupdate.ntpValue();
			log.info("NTP Value difference: " + ntpdiff + " Message: " + dialog.getMessage());
			
			musermail_id = 0;
			
			if (isTimeStampOK(sentTimestamp, lastMUserupdate)){
			
				//Strip html tags from the message with Jsoup
				String html = dialog.getMessage();
				Document doc = Jsoup.parse(html);
				String stripText = doc.body().text();
				log.info("MilbtnAction.html: " + html);
				log.info("MailbtnAction.text: " + stripText);
				
				EMail eMail = null;
			
			eMail = new EMail(m_client, dialog.getFrom().getInternetAddress().toString(), dialog.getTo(), dialog.getSubject(),stripText);
			
			
			
			if(r_mailtext_id != 1)//it's not a blank email
			{
			MUserMail userMail = new MUserMail(m_MailText, m_user.getAD_User_ID(), eMail);
			userMail.setMessageID(lastMUserMailrec.getMessageID());
			userMail.saveEx();
			}
			else
			{
				MUserMail userMail = new MUserMail (Env.getCtx(), eMail);
				userMail.setAD_User_ID(m_AD_User_ID);
				userMail.setMessageID(lastMUserMailrec.getMessageID());
				userMail.saveEx();
			}
			}
		
		}//fixMuserMailrecord()
		
	/**
	 * If the timestamp is OK then the selected record is almost certainly a the one we want to copy.
	 * @param whenOK
	 * @param dialogcreated
	 * @return
	 */
	private boolean isTimeStampOK(TimeStamp whenOK, TimeStamp dialogcreated){
			if(whenOK.ntpValue() - dialogcreated.ntpValue() <1000){
				return true;
			}
			else
			{
				return false;
			}
		}
		
	
	
	/**
	 * The getTemplates() method returns an List<List<Object>> containing String[] Objects, which need to be cast to something we can use.
	 * Take the List<List<Object>>, iterate and copy values to a new List<KeyNamePair>. This has to be the hard way?!
	 * @param query
	 * @param para
	 * @return
	 */
	private List<KeyNamePair> getKeyNamePair(String query, Integer para){
			
			List<KeyNamePair> kvp	 = new ArrayList<KeyNamePair>();
			List<Object> holdingList = new ArrayList<Object>();
			
			String sqlQuery = query;
			Integer sqlPara = para;
			
			String ss = null;
			Integer ii = null;
			templateRaw = getTemplates(sqlQuery, sqlPara);
			
			
			for (List<Object> raw : templateRaw){
				  Iterator<Object> itr = raw.iterator();
			      while(itr.hasNext()) {
			         Object element = itr.next();
			         holdingList.add(element);
			      }
				
			}
			
			for (Object obj : holdingList){
				String s = null;
				Integer i = null;
				
				if(obj.getClass() == BigDecimal.class){
					i = new Integer(obj.toString());
					}
					if(obj.getClass() == String.class){
					s = obj.toString();
					}
				if(s == null) ii=i;
				if(i == null) ss=s;
				
				if(ss != null && ii != null){//Catch the loop when there's 2 values to write to the Arraylist
					kvp.add(new KeyNamePair(ii.intValue(),ss));
					ii=null;//Set the 2 variables back to null for next iteration.
					ss=null;
					}
				
			}
			return kvp;
	}
	

	/**
	 * @param query
	 * @param para
	 * @return
	 */
	private List<List<Object>> getTemplates(String query, Integer para ) {
			
			List<List<Object>> emailTemplates = new ArrayList<List<Object>>();
		
			/* Create ArrayList parameter with one arg, AD_ClientID. 
			 * Note that DB.getSQLArrayObjectsEx takes an Object as the 'para' arg so an array could be used instead of an Integer
			 */
			
			Integer parameter = para;
			emailTemplates = DB.getSQLArrayObjectsEx(null, query, parameter);
			
		
			return emailTemplates;
			
		}
}
