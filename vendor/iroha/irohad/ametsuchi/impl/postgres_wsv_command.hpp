/**
 * Copyright Soramitsu Co., Ltd. 2017 All Rights Reserved.
 * http://soramitsu.co.jp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef IROHA_POSTGRES_WSV_COMMAND_HPP
#define IROHA_POSTGRES_WSV_COMMAND_HPP

#include "ametsuchi/wsv_command.hpp"

#include <set>
#include <string>

#include "ametsuchi/impl/postgres_wsv_common.hpp"

namespace iroha {
  namespace ametsuchi {

    class PostgresWsvCommand : public WsvCommand {
     public:
      explicit PostgresWsvCommand(pqxx::nontransaction &transaction);
      WsvCommandResult insertRole(
          const shared_model::interface::types::RoleIdType &role_name) override;

      WsvCommandResult insertAccountRole(
          const shared_model::interface::types::AccountIdType &account_id,
          const shared_model::interface::types::RoleIdType &role_name) override;
      WsvCommandResult deleteAccountRole(
          const shared_model::interface::types::AccountIdType &account_id,
          const shared_model::interface::types::RoleIdType &role_name) override;

      WsvCommandResult insertRolePermissions(
          const shared_model::interface::types::RoleIdType &role_id,
          const std::set<shared_model::interface::types::PermissionNameType>
              &permissions) override;

      WsvCommandResult insertAccount(
          const shared_model::interface::Account &account) override;
      WsvCommandResult updateAccount(
          const shared_model::interface::Account &account) override;
      WsvCommandResult setAccountKV(
          const shared_model::interface::types::AccountIdType &account_id,
          const shared_model::interface::types::AccountIdType
              &creator_account_id,
          const std::string &key,
          const std::string &val) override;
      WsvCommandResult insertAsset(
          const shared_model::interface::Asset &asset) override;
      WsvCommandResult upsertAccountAsset(
          const shared_model::interface::AccountAsset &asset) override;
      WsvCommandResult insertSignatory(
          const shared_model::interface::types::PubkeyType &signatory) override;
      WsvCommandResult insertAccountSignatory(
          const shared_model::interface::types::AccountIdType &account_id,
          const shared_model::interface::types::PubkeyType &signatory) override;
      WsvCommandResult deleteAccountSignatory(
          const shared_model::interface::types::AccountIdType &account_id,
          const shared_model::interface::types::PubkeyType &signatory) override;
      WsvCommandResult deleteSignatory(
          const shared_model::interface::types::PubkeyType &signatory) override;
      WsvCommandResult insertPeer(
          const shared_model::interface::Peer &peer) override;
      WsvCommandResult deletePeer(
          const shared_model::interface::Peer &peer) override;
      WsvCommandResult insertDomain(
          const shared_model::interface::Domain &domain) override;
      WsvCommandResult insertAccountGrantablePermission(
          const shared_model::interface::types::AccountIdType
              &permittee_account_id,
          const shared_model::interface::types::AccountIdType &account_id,
          const shared_model::interface::types::PermissionNameType
              &permission_id) override;

      WsvCommandResult deleteAccountGrantablePermission(
          const shared_model::interface::types::AccountIdType
              &permittee_account_id,
          const shared_model::interface::types::AccountIdType &account_id,
          const shared_model::interface::types::PermissionNameType
              &permission_id) override;

     private:
      const size_t default_tx_counter = 0;

      pqxx::nontransaction &transaction_;

      using ExecuteType = decltype(makeExecuteResult(transaction_));
      ExecuteType execute_;

      /**
       * Transforms result which contains pqxx to WsvCommandResult,
       * which will have error message generated by error_generator
       * appended to error received from given result
       * @param result which can be received by calling execute_
       * @param error_generator function which must generate error message
       * to be used as a return error.
       * Function is passed instead of string to avoid overhead of string
       * construction in successful case.
       * @return WsvCommandResult with combined error message
       * in case of result contains error
       */
      template <typename Function>
      WsvCommandResult makeCommandResult(
          expected::Result<pqxx::result, std::string> &&result,
          Function &&error_generator) const noexcept {
        return result.match(
            [](expected::Value<pqxx::result> v) -> WsvCommandResult {
              return {};
            },
            [&error_generator](
                expected::Error<std::string> e) -> WsvCommandResult {
              return expected::makeError(error_generator() + "\n" + e.error);
            });
      }
    };
  }  // namespace ametsuchi
}  // namespace iroha

#endif  // IROHA_POSTGRES_WSV_COMMAND_HPP
