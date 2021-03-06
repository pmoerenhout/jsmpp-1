/*
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package org.jsmpp.examples;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import org.apache.log4j.BasicConfigurator;
import org.jsmpp.PDUStringException;
import org.jsmpp.SMPPConstant;
import org.jsmpp.bean.CancelSm;
import org.jsmpp.bean.DataCodings;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliveryReceipt;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.GSMSpecificFeature;
import org.jsmpp.bean.InterfaceVersion;
import org.jsmpp.bean.MessageMode;
import org.jsmpp.bean.MessageType;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.QuerySm;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.ReplaceSm;
import org.jsmpp.bean.SMSCDeliveryReceipt;
import org.jsmpp.bean.SubmitMulti;
import org.jsmpp.bean.SubmitMultiResult;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.bean.UnsuccessDelivery;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.BindRequest;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.QuerySmResult;
import org.jsmpp.session.SMPPServerSession;
import org.jsmpp.session.SMPPServerSessionListener;
import org.jsmpp.session.ServerMessageReceiverListener;
import org.jsmpp.session.ServerResponseDeliveryAdapter;
import org.jsmpp.session.Session;
import org.jsmpp.util.DeliveryReceiptState;
import org.jsmpp.util.HexUtil;
import org.jsmpp.util.MessageIDGenerator;
import org.jsmpp.util.MessageId;
import org.jsmpp.util.RandomMessageIDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author uudashr
 */
public class SMPPServerSimulatorTest extends ServerResponseDeliveryAdapter
    implements Runnable, ServerMessageReceiverListener {
  private static final Integer DEFAULT_PORT = 8056;
  private static final Logger logger = LoggerFactory.getLogger(SMPPServerSimulatorTest.class);
  private final ExecutorService execService = Executors.newFixedThreadPool(5);
  private final ExecutorService execServiceDelReceipt = Executors.newFixedThreadPool(100);
  private final MessageIDGenerator messageIDGenerator = new RandomMessageIDGenerator();
  private int port;

  public SMPPServerSimulatorTest(int port) {
    this.port = port;
  }

  public static void main(String[] args) {
    int port;
    try {
      port = Integer.parseInt(System.getProperty("jsmpp.simulator.port", DEFAULT_PORT.toString()));
    }
    catch (NumberFormatException e) {
      port = DEFAULT_PORT;
    }
    BasicConfigurator.configure();
    SMPPServerSimulatorTest smppServerSim = new SMPPServerSimulatorTest(port);
    logger.info("run {}", smppServerSim);
    smppServerSim.run();
    logger.info("run {} done", smppServerSim);
  }

  public void run() {
//    logger.info("messageId {}", messageIDGenerator.newMessageId());
//    logger.info("messageId {}", messageIDGenerator.newMessageId());
//    logger.info("messageId {}", messageIDGenerator.newMessageId());
    try {
      SMPPServerSessionListener sessionListener = new SMPPServerSessionListener(port);

      logger.info("Listening on port {}", port);
      while (true) {
        SMPPServerSession serverSession = sessionListener.accept();
        logger.info("Accepting connection for session {}", serverSession.getSessionId());
        serverSession.setMessageReceiverListener(this);
        serverSession.setResponseDeliveryListener(this);
        execService.execute(new WaitBindTask(serverSession));
//                try {
//                    Thread.sleep(60 *   60 * 1000L);
//                }
//                catch (InterruptedException e)
//                {
//                }
//                serverSession.close();
      }
      //logger.info("close listener {}", sessionListener);
      //sessionListener.close();
      //execService.shutdown();

    }
    catch (IOException e) {
      logger.error("IO error occurred: {}", e.getMessage());
      logger.error("IO error occurred", e);
    }
  }

  public QuerySmResult onAcceptQuerySm(QuerySm querySm,
                                       SMPPServerSession source) throws ProcessRequestException {
    logger.info("Accepting query_sm, but not implemented");
    return null;
  }

  public MessageId onAcceptSubmitSm(SubmitSm submitSm,
                                    SMPPServerSession source) throws ProcessRequestException {
    logger.info("onAcceptSubmitSm");
    MessageId messageId = messageIDGenerator.newMessageId();
    logger
        .debug("Receiving submit_sm '{}', and return message id {}", new String(submitSm.getShortMessage()), messageId);
    logger.info("Receiving submit_sm '{}', and return message id {}",
        HexUtil.conventBytesToHexString(submitSm.getShortMessage()), messageId);
    if (SMSCDeliveryReceipt.FAILURE.containedIn(submitSm.getRegisteredDelivery()) || SMSCDeliveryReceipt.SUCCESS_FAILURE
        .containedIn(submitSm.getRegisteredDelivery())) {
      execServiceDelReceipt.execute(new DeliveryReceiptTask(source, submitSm, messageId));
    }
    else {
      logger.debug("Not sending a delivery receipt: {}",
          HexUtil.conventBytesToHexString(new byte[]{ submitSm.getRegisteredDelivery() }));
    }
    if (new String(submitSm.getShortMessage()).equals("close")) {
      source.close();
    }
    logger.info("onAcceptSubmitSm, done");
    return messageId;
  }

  public void onSubmitSmRespSent(MessageId messageId,
                                 SMPPServerSession source) {
    logger.debug("submit_sm_resp with message_id {} has been sent", messageId);
  }

  public SubmitMultiResult onAcceptSubmitMulti(SubmitMulti submitMulti,
                                               SMPPServerSession source) throws ProcessRequestException {
    MessageId messageId = messageIDGenerator.newMessageId();
    logger.debug("Receiving submit_multi_sm '{}', and return message id {}",
        new String(submitMulti.getShortMessage()),
        messageId);
    if (SMSCDeliveryReceipt.FAILURE.containedIn(submitMulti.getRegisteredDelivery())
        || SMSCDeliveryReceipt.SUCCESS_FAILURE.containedIn(submitMulti.getRegisteredDelivery())) {
      execServiceDelReceipt.execute(new DeliveryReceiptTask(source, submitMulti, messageId));
    }

    return new SubmitMultiResult(messageId.getValue(), new UnsuccessDelivery[0]);
  }

  public DataSmResult onAcceptDataSm(DataSm dataSm, Session source)
      throws ProcessRequestException {
    return null;
  }

  public void onAcceptCancelSm(CancelSm cancelSm, SMPPServerSession source)
      throws ProcessRequestException {
  }

  public void onAcceptReplaceSm(ReplaceSm replaceSm, SMPPServerSession source)
      throws ProcessRequestException {
  }

  private static class WaitBindTask implements Runnable {
    private final SMPPServerSession serverSession;

    public WaitBindTask(SMPPServerSession serverSession) {
      this.serverSession = serverSession;
    }

    public void run() {
      try {
        BindRequest bindRequest = serverSession.waitForBind(5000);
        logger.info("Accepting bind for session {}, interface version {}", serverSession.getSessionId(),
            bindRequest.getInterfaceVersion());
        try {
          bindRequest.accept("sys", InterfaceVersion.IF_34);
        }
        catch (PDUStringException e) {
          logger.error("Invalid system id", e);
          bindRequest.reject(SMPPConstant.STAT_ESME_RSYSERR);
        }
        serverSession.setEnquireLinkTimer(0);
      }
      catch (IllegalStateException e) {
        logger.error("System error", e);
      }
      catch (TimeoutException e) {
        logger.warn("Wait for bind has reached timeout", e);
      }
      catch (IOException e) {
        logger.error("Failed accepting bind request for session {}", serverSession.getSessionId());
        logger.error("Failed accepting bind request for session", e);
      }
    }
  }

  private static class DeliveryReceiptTask implements Runnable {
    private final SMPPServerSession session;
    private final MessageId messageId;

    private final TypeOfNumber sourceAddrTon;
    private final NumberingPlanIndicator sourceAddrNpi;
    private final String sourceAddress;

    private final TypeOfNumber destAddrTon;
    private final NumberingPlanIndicator destAddrNpi;
    private final String destAddress;

    private final int totalSubmitted;
    private final int totalDelivered;

    private final byte[] shortMessage;

    public DeliveryReceiptTask(SMPPServerSession session,
                               SubmitSm submitSm, MessageId messageId) {
      this.session = session;
      this.messageId = messageId;

      // reversing destination to source
      sourceAddrTon = TypeOfNumber.valueOf(submitSm.getDestAddrTon());
      sourceAddrNpi = NumberingPlanIndicator.valueOf(submitSm.getDestAddrNpi());
      sourceAddress = submitSm.getDestAddress();

      // reversing source to destination
      destAddrTon = TypeOfNumber.valueOf(submitSm.getSourceAddrTon());
      destAddrNpi = NumberingPlanIndicator.valueOf(submitSm.getSourceAddrNpi());
      destAddress = submitSm.getSourceAddr();

      totalSubmitted = totalDelivered = 1;

      shortMessage = submitSm.getShortMessage();
    }

    public DeliveryReceiptTask(SMPPServerSession session,
                               SubmitMulti submitMulti, MessageId messageId) {
      this.session = session;
      this.messageId = messageId;

      // set to unknown and null, since it was submit_multi
      sourceAddrTon = TypeOfNumber.UNKNOWN;
      sourceAddrNpi = NumberingPlanIndicator.UNKNOWN;
      sourceAddress = null;

      // reversing source to destination
      destAddrTon = TypeOfNumber.valueOf(submitMulti.getSourceAddrTon());
      destAddrNpi = NumberingPlanIndicator.valueOf(submitMulti.getSourceAddrNpi());
      destAddress = submitMulti.getSourceAddr();

      // distribution list assumed only contains single address
      totalSubmitted = totalDelivered = submitMulti.getDestAddresses().length;

      shortMessage = submitMulti.getShortMessage();
    }

    public void run() {
      logger.debug("DeliveryReceiptTask run");
//      try {
//        Thread.sleep(1000);
//      }
//      catch (InterruptedException e1) {
//        e1.printStackTrace();
//      }
      SessionState state = session.getSessionState();
      if (!state.isReceivable()) {
        logger.debug("Not sending delivery receipt for message id {} since session state is {}", messageId, state);
        return;
      }
      logger.debug("messageId {}", messageId);
      String stringValue = Integer.valueOf(messageId.getValue(), 16).toString();
      logger.debug("stringValue {}", stringValue);
      try {

        DeliveryReceipt delRec = new DeliveryReceipt(stringValue, totalSubmitted, totalDelivered, new Date(),
            new Date(), DeliveryReceiptState.DELIVRD, "000", new String(shortMessage));
        session.deliverShortMessage(
            "mc",
            sourceAddrTon,
            sourceAddrNpi,
            sourceAddress,
            destAddrTon,
            destAddrNpi,
            destAddress,
            new ESMClass(MessageMode.DEFAULT, MessageType.SMSC_DEL_RECEIPT, GSMSpecificFeature.DEFAULT),
            (byte) 0,
            (byte) 0,
            new RegisteredDelivery(0),
            DataCodings.ZERO,
            delRec.toString().getBytes());
        logger.debug("Sending delivery receipt for message id {}: {}", messageId, stringValue);
      }
      catch (Exception e) {
        logger.error("Failed sending delivery_receipt for message id " + messageId + ":" + stringValue, e);
      }
    }
  }
}
