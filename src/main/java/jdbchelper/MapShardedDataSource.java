package jdbchelper;

import java.util.HashMap;

/**
 * User: erdinc
 * Date: Oct 16, 2009
 * Time: 11:02:02 AM
 */
public class MapShardedDataSource extends HashMap<Integer, LoadBalancingDataSource> implements ShardedDataSource {
   @Override
   public LoadBalancingDataSource getDataSource(int shardNo) {
      return get(shardNo);
   }

   @Override
   public int getShardCount() {
      return size();
   }

   @Override
   public void runMaintenanceJob() {
      for (LoadBalancingDataSource ds : values()) {
         ds.getMaintenanceJob().run();
      }
   }
}
