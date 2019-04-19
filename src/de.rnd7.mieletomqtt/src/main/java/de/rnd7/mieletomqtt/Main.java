package de.rnd7.mieletomqtt;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

import de.rnd7.mieletomqtt.config.Config;
import de.rnd7.mieletomqtt.config.ConfigParser;
import de.rnd7.mieletomqtt.miele.MieleAPI;
import de.rnd7.mieletomqtt.miele.MieleDevice;
import de.rnd7.mieletomqtt.mqtt.GwMqttClient;

public class Main {

	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

	private final Config config;

	private final EventBus eventBus = new EventBus();

	private final MieleAPI mieleAPI;

	@SuppressWarnings("squid:S2189")
	public Main(final Config config) {
		this.config = config;
		this.eventBus.register(new GwMqttClient(config));
		this.mieleAPI = new MieleAPI(this.config.getMieleClientId(), this.config.getMieleClientSecret(),
				this.config.getMieleUsername(), this.config.getMielePassword(), config.getTimezone());

		try {
			final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
			executor.scheduleAtFixedRate(this::exec, 0, config.getPollingInterval().getSeconds(), TimeUnit.SECONDS);
			executor.scheduleAtFixedRate(this.mieleAPI::updateToken, 2, 2, TimeUnit.HOURS);

			while (true) {
				this.sleep();
			}
		} catch (final Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	private void exec() {
		try {
			for (final MieleDevice mieleDevice : this.mieleAPI.fetchDevices()) {
				this.eventBus.post(mieleDevice.toFullMessage());
				this.eventBus.post(mieleDevice.toSmallMessage());
			}
		} catch (final Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	private void sleep() {
		try {
			Thread.sleep(100);
		} catch (final InterruptedException e) {
			LOGGER.debug(e.getMessage(), e);
			Thread.currentThread().interrupt();
		}
	}

	public static void main(final String[] args) {
		if (args.length != 1) {
			LOGGER.error("Expected configuration file as argument");
			return;
		}

		try {
			new Main(ConfigParser.parse(new File(args[0])));
		} catch (final IOException e) {
			LOGGER.error(e.getMessage(), e);
		}
	}
}
