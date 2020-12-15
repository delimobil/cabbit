A cabbit is a fictional hybrid between a cat and a rabbit.

The main goal of the library is to wrap rabbit-client with cats-effects & fs2. The wrapper is intended to be thin and resemble the client. However,
- It's widely spread to separate producer, declarator and publisher, thus it is mentioned in algebra.
- Client's channel is not thread safe, hence a fixed thread pool is assigned to every channel.

Ready to start? Follow the white cabbit into Fs2RabbitSuite.
