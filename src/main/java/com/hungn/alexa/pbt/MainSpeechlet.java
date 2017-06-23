package com.hungn.alexa.pbt;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.*;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.hungn.alexa.pbt.api.GoogleMapsGeoencoding;
import com.hungn.alexa.pbt.api.PennRides;
import com.hungn.alexa.pbt.types.IntentName;
import com.hungn.alexa.pbt.types.VehicleStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;

/**
 * @author Hung Nguyen (hungng@seas.upenn.edu)
 */
public class MainSpeechlet implements Speechlet {
  private static final Logger log = LoggerFactory.getLogger(MainSpeechlet.class);

  private static final PennRides pr = new PennRides();
  private static final GoogleMapsGeoencoding maps = new GoogleMapsGeoencoding();

  @Override
  public void onSessionStarted(final SessionStartedRequest request, final Session session)
      throws SpeechletException {
    if (log.isDebugEnabled()) {
      log.debug("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
          session.getSessionId());
    }
  }

  @Override
  public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
      throws SpeechletException {
    if (log.isDebugEnabled()) {
      log.debug("onLaunch requestId={}, sessionId={}", request.getRequestId(),
          session.getSessionId());
    }

    return getHelpResponse();
  }

  @Override
  public SpeechletResponse onIntent(final IntentRequest request, final Session session)
      throws SpeechletException {
    Intent intent = request.getIntent();
    String intentName = (intent != null) ? intent.getName() : null;

    if (intentName == null) {
      if (log.isInfoEnabled()) {
        log.info("Empty intent - requestId={}, sessionId={}", request.getRequestId(),
            session.getSessionId());
      }
      throw new SpeechletException("Invalid Intent");
    } else if (IntentName.AMZ_HELP_INTENT.equals(intentName)) {
      return getHelpResponse();
    } else if (IntentName.AMZ_CANCEL_INTENT.equals(intentName) ||
        IntentName.AMZ_STOP_INTENT.equals(intentName)) {
      return getTellResponse("Goodbye!");
    } else if (IntentName.TRACK_INTENT.equals(intentName)) {
      String vehicle = intent.getSlot("vehicle").getValue();
      if (log.isDebugEnabled()) {
        log.debug("onIntent requestId={}, sessionId={} -- track vehicle: {}", request.getRequestId(),
            session.getSessionId(), vehicle == null ? "NULL" : vehicle);
      }

      if (vehicle == null || vehicle.isEmpty()) {
        return getResponse("Sorry, I can't recognize the route you mentioned. Please try again!",
            "You can ask question such as Where is Shuttle East");
      } else {
        PennRides.Route route = null;
        switch (vehicle.toLowerCase()) {
          case "shuttle east":
            route = PennRides.Route.SHUTTLE_EAST;
            break;
          case "shuttle west a":
            route = PennRides.Route.SHUTTLE_WEST_A;
            break;
          case "shuttle west b":
            route = PennRides.Route.SHUTTLE_WEST_B;
            break;
          case "bus east":
            route = PennRides.Route.BUS_EAST;
            break;
          case "bus west":
            route = PennRides.Route.BUS_WEST;
            break;
          default:
            // ignore
        }

        if (route != null) {
          // Only proceed with valid route
          LinkedList<VehicleStatus> vehicles = pr.getVehicleStatus(route);

          if (vehicles == null) {
            return getTellResponse("Cannot connect to Penn Ride. Please try again later!");
          } else if (vehicles.size() == 0) {
            return getTellResponse("There is no vehicle operating on "
                + vehicle + " route currently. " +
                "Please check Penn Transit website for up to date schedule.");
          } else {
            String message = "There are " + vehicles.size() + " vehicles operating on "
                + vehicle + " route currently. ";
            for (VehicleStatus status : vehicles) {
              String location = maps.getStreetAddress(status.getLongitude(), status.getLatitude());
              if (location != null && !location.isEmpty()) {
                message += status.getName() + " was at " + location + " and heading " + status.getHeading() +
                    " " + status.getLastUpdated() + ". ";
              }
            }
            return getTellResponse(message);
          }
        } else {
          return getResponse("There is no route named " + vehicle.toLowerCase() + "!",
              "You can ask question such as Where is Shuttle East");
        }
      }
    }

    if (log.isDebugEnabled()) {
      log.debug("UnknownIntent requestId={}, sessionId={} -- intent: {}", request.getRequestId(),
          session.getSessionId(), intentName);
    }
    return getTellResponse("Goodbye!");
  }

  @Override
  public void onSessionEnded(final SessionEndedRequest request, final Session session)
      throws SpeechletException {
    if (log.isDebugEnabled()) {
      log.debug("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
          session.getSessionId());
    }
  }

  /**
   * Helper function to simplify response generation
   *
   * @param tellMsg     message to be told by Alexa
   * @param repromptMsg message to be told by Alexa if user not respond
   * @return Alexa response Speechlet object
   */
  private SpeechletResponse getResponse(String tellMsg, String repromptMsg) {
    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    Reprompt reprompt = new Reprompt();
    PlainTextOutputSpeech repromptSpeech = new PlainTextOutputSpeech();
    reprompt.setOutputSpeech(repromptSpeech);

    speech.setText(tellMsg);
    repromptSpeech.setText(repromptMsg);

    return SpeechletResponse.newAskResponse(speech, reprompt);
  }

  /**
   * Helper function to simplify response generation
   *
   * @param msg message to be told by Alexa
   * @return Alexa response Speechlet object
   */
  private SpeechletResponse getTellResponse(String msg) {
    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    speech.setText(msg);

    return SpeechletResponse.newTellResponse(speech);
  }

  /**
   * Helper function to simplify help response generation
   *
   * @return Alexa response Speechlet object for help
   */
  private SpeechletResponse getHelpResponse() {
    PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
    Reprompt reprompt = new Reprompt();
    PlainTextOutputSpeech repromptSpeech = new PlainTextOutputSpeech();
    reprompt.setOutputSpeech(repromptSpeech);

    speech.setText("You can ask for Penn Bus location such as Shuttle East," +
        " Shuttle West A, Shuttle West B, Bus East, and Bus West");
    repromptSpeech.setText("You can ask question such as Where is Shuttle East");

    return SpeechletResponse.newAskResponse(speech, reprompt);
  }
}
