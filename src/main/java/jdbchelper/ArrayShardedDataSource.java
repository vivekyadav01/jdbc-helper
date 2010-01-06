package jdbchelper;

import javax.sql.DataSource;

/**
 * A Sharded DataSource with a fixed number of shards.
 *
 * You can not increase the number of shards at runtime,
 * the DataSources are kept in an Array.
 */
public class ArrayShardedDataSource implements ShardedDataSource {
   protected LoadBalancingDataSource[] dataSources;

   public DataSource getDataSource(int shardNo) {
      return dataSources[shardNo];
   }

   public int getShardCount() {
      return dataSources.length;
   }

   public void runMaintenanceJob() {
      for (LoadBalancingDataSource dataSource : dataSources) {
         dataSource.getMaintenanceJob().run();
      }
   }

   public ArrayShardedDataSource(int shardCount) {
      dataSources = new LoadBalancingDataSource[shardCount];
   }

   public void setDataSource(int shard, LoadBalancingDataSource dataSource) {
      dataSources[shard] = dataSource;
   }

   public ArrayShardedDataSource(LoadBalancingDataSource... dataSources) {
      this.dataSources = dataSources;
   }
}
