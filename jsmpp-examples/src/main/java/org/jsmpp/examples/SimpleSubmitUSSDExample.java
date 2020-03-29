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

import org.jsmpp.InvalidResponseException;
import org.jsmpp.PDUException;
import org.jsmpp.bean.Alphabet;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.GeneralDataCoding;
import org.jsmpp.bean.MessageClass;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.SMSCDeliveryReceipt;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.util.AbsoluteTimeFormatter;
import org.jsmpp.util.TimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pmoerenhout
 */
public class SimpleSubmitUSSDExample {
  private static final Logger LOGGER = LoggerFactory.getLogger(SimpleSubmitUSSDExample.class);
  private static final TimeFormatter TIME_FORMATTER = new AbsoluteTimeFormatter();

  public static void main(String[] args) {
    SMPPSession session = new SMPPSession();
    try {
      LOGGER.info("Connecting");
      String systemId = session.connectAndBind("localhost", 2775,
          new BindParameter(BindType.BIND_TRX, "j", "jpwda", "smpp", TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN, null));
      LOGGER.info("Connected with Message Centre with system id {}", systemId);

      try {
        // Optional parameter Dest_bearer_type
        // 0x04 = USSD
        // Optional parameter Ussd_service_op
        // 0x02 = USSR Request
        String messageId = session.submitShortMessage("USSD",
            TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.UNKNOWN, "1616",
            TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.UNKNOWN, "628176504657",
            new ESMClass(), (byte) 0, (byte) 1, TIME_FORMATTER.format(new Date()), null,
            new RegisteredDelivery(SMSCDeliveryReceipt.DEFAULT), (byte) 0, new GeneralDataCoding(Alphabet.ALPHA_DEFAULT, MessageClass.CLASS1, false), (byte) 0,
            "*#100#".getBytes(), new OptionalParameter.Dest_bearer_type((byte) 0x04), new OptionalParameter.Ussd_service_op((byte) 0x02));
        LOGGER.info("Message submitted, message_id is {}", messageId);
      } catch (PDUException e) {
        // Invalid PDU parameter
        LOGGER.error("Invalid PDU parameter", e);
      } catch (ResponseTimeoutException e) {
        // Response timeout
        LOGGER.error("Response timeout", e);
      } catch (InvalidResponseException e) {
        // Invalid response
        LOGGER.error("Receive invalid response", e);
      } catch (NegativeResponseException e) {
        // Receiving negative response (non-zero command_status)
        LOGGER.error("Receive negative response, e");
      } catch (IOException e) {
        LOGGER.error("IO error occured", e);
      }

      session.unbindAndClose();

    } catch (IOException e) {
      LOGGER.error("Failed connect and bind to host", e);
    }
  }

}