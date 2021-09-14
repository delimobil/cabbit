cabbit
=============

A [cabbit] is a fictional hybrid between a cat and a rabbit.

The main goal of the library is to wrap rabbit-client with cats-effects & fs2. The wrapper is intended to be thin and resemble the client. However,
- It's widely spread to separate producer, declarator and publisher, thus it is mentioned in algebra.
- Client's channel is not thread safe, hence a dedicated fixed thread pool is assigned to every channel.

Ready to start? Follow the white cabbit to CabbitSuite.

[cabbit]: https://en.wikipedia.org/wiki/Cabbit

### LICENSE ###
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
