package collision_detection;

import android.util.Log;

import java.util.List;

public class CollisionpredictModule {
    float set_alarmtime = 5.0f; //충돌 알림 시간
    float collisionRadius = 0.4f; // 충돌 반경


    CollisionWarning processCollision(List<Object_Info> activeObjects, float[] userPosition, float[] userVelocity  ){
        double min = 100;
        double tClosest=0;
        Object_Info alarm_object =null;
        for(Object_Info object: activeObjects){

            tClosest = predictCollision(userPosition, userVelocity,object);

            if(tClosest<=0){
                //Log.d("collision time","No collision");
                continue;
            }
            else if(tClosest <min){
                //Log.d("collision time","t: "+String.format("%.2f",tClosest));
                min = tClosest;
                alarm_object = object;

            }
        }
        if(alarm_object ==null || tClosest<=0){
            //Log.d("collision time","NO WARNING");
            return null;
        }
        else {
            //Log.d("collision time","Final WARNING: "+alarm_object.getlabel()+" in "+String.format("%.2f",min)+"s");
            return new CollisionWarning(alarm_object, min);
        }
    }

    //user가 A, Object가 B, A를 기준으로 B의 속도
    public double predictCollision(float[] positionA, float[] velocityA,
                                    Object_Info object) {
        float[] positionB = object.getPosition();
        float[] velocityB = object.getVelocity();
        double[] relativePosition = {
                positionB[0] - positionA[0],
                positionB[1] - positionA[1],
                positionB[2] - positionA[2]
        };
        double[] relativeVelocity = {
                velocityB[0] - velocityA[0],
                velocityB[1] - velocityA[1],
                velocityB[2] - velocityA[2]
        };

        double tClosest = calculateClosestTime(relativePosition, relativeVelocity);
        double minDistance = calculateMinDistance(relativePosition, relativeVelocity, tClosest);
//        String pos = String.format("(%.2f, %.2f, %.2f)",relativePosition[0],relativePosition[1],relativePosition[2]);
//        String vel = String.format("(%.2f, %.2f, %.2f)",relativeVelocity[0],relativeVelocity[1],relativeVelocity[2]);
//        String cDistance = String.format("%.2f",Math.sqrt(Math.pow(relativePosition[0],2)+Math.pow(relativePosition[1],2)+Math.pow(relativePosition[2],2)));
//        String v = String.format("%.2f",Math.sqrt(Math.pow(relativeVelocity[0],2)+Math.pow(relativeVelocity[1],2)+Math.pow(relativeVelocity[2],2)));
//        Log.d("predicted","id:"+object.getid()+" "+object.getlabel()+", p:"+pos+ ", v:"+vel+", cDistance:"+cDistance);
//        Log.d("predicted","id:"+object.getid()+" "+object.getlabel()+", tClosest:"+String.format("%.2f",tClosest)+", mDistance:"+String.format("%.3f",minDistance));
        if (tClosest < 0 || tClosest > set_alarmtime ) {
            return 0; // 충돌 가능성이 없거나 한참 후
        }


        if(minDistance <= collisionRadius){ //충돌상황
            return tClosest;
        }
        else{
            return 0;
        }
    }

    private double calculateClosestTime(double[] relativePosition, double[] relativeVelocity) {
        double dotProduct = relativePosition[0] * relativeVelocity[0]
                + relativePosition[1] * relativeVelocity[1]
                + relativePosition[2] * relativeVelocity[2];
        double velocityNormSquared = relativeVelocity[0] * relativeVelocity[0]
                + relativeVelocity[1] * relativeVelocity[1]
                + relativeVelocity[2] * relativeVelocity[2];
        return -dotProduct / velocityNormSquared;
    }


    private double calculateMinDistance(double[] relativePosition, double[] relativeVelocity, double tClosest) {
        double[] closestRelativePosition = {
                relativePosition[0] + tClosest * relativeVelocity[0],
                relativePosition[1] + tClosest * relativeVelocity[1],
                relativePosition[2] + tClosest * relativeVelocity[2]
        };
        return Math.sqrt(
                closestRelativePosition[0] * closestRelativePosition[0] +
                        closestRelativePosition[1] * closestRelativePosition[1] +
                        closestRelativePosition[2] * closestRelativePosition[2]
        );
    }


}
