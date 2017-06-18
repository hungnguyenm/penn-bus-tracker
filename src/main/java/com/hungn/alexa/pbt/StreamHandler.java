package com.hungn.alexa.pbt;

import com.amazon.speech.speechlet.lambda.SpeechletRequestStreamHandler;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Hung Nguyen (hungng@seas.upenn.edu)
 */
public class StreamHandler extends SpeechletRequestStreamHandler {
  private static final Set<String> supportedApplicationIds = new HashSet<String>();

  static {
    supportedApplicationIds.add("amzn1.ask.skill.cbae9e95-311e-4cf8-af56-c815f5a83b37");
  }

  public StreamHandler() {
    super(new MainSpeechlet(), supportedApplicationIds);
  }
}