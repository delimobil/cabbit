package ru.delimobil.cabbit

import com.rabbitmq.client.Delivery
import com.rabbitmq.client.GetResponse
import ru.delimobil.cabbit.model.DeliveryTag

object syntax {

  implicit class deliveryOps(val delivery: Delivery) extends AnyVal {
    def deliveryTag: DeliveryTag = DeliveryTag(delivery.getEnvelope.getDeliveryTag)
  }

  implicit class getResponseOps(val getResponse: GetResponse) extends AnyVal {
    def deliveryTag: DeliveryTag = DeliveryTag(getResponse.getEnvelope.getDeliveryTag)
  }
}
